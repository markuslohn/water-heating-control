package de.bimalo.homeauto.control.heatingcontrol;

import de.bimalo.homeauto.boundary.e3dc.E3dcAdapter;
import de.bimalo.homeauto.boundary.elwa2.Elwa2Adapter;
import de.bimalo.homeauto.boundary.viessman.VitodensAdapter;
import de.bimalo.homeauto.control.manualwaterheating.ManualWaterHeatingPolicy;
import de.bimalo.homeauto.entity.BatteryStatus;
import de.bimalo.homeauto.entity.GasHeatingStatus;
import de.bimalo.homeauto.entity.HeatingDecision;
import de.bimalo.homeauto.entity.HeatingRodStatus;
import de.bimalo.homeauto.entity.ManualHeatingState;
import de.bimalo.homeauto.entity.Power;
import de.bimalo.homeauto.entity.Season;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/**
 * Service that controls the heating rod based on solar power surplus and
 * temperature.
 * Runs periodically to check conditions and adjust heating power accordingly.
 */
@Slf4j
@ApplicationScoped
public class HeatingControlService {

    private final HeatingControlConfig config;
    private final E3dcAdapter e3dcAdapter;
    private final Elwa2Adapter elwa2Adapter;
    private final VitodensAdapter vitodensAdapter;

    private final AutomaticHeatingPolicy automaticPolicy;
    private final ManualWaterHeatingPolicy manualPolicy;

    // Runtime override for battery priority (resets at midnight)
    private final AtomicBoolean batteryPriorityRuntimeOverride = new AtomicBoolean(false);

    @Inject
    public HeatingControlService(
            HeatingControlConfig config,
            E3dcAdapter e3dcAdapter,
            Elwa2Adapter elwa2Adapter,
            VitodensAdapter vitodensAdapter,
            AutomaticHeatingPolicy automaticPolicy,
            ManualWaterHeatingPolicy manualPolicy) {
        this.config = config;
        this.e3dcAdapter = e3dcAdapter;
        this.elwa2Adapter = elwa2Adapter;
        this.vitodensAdapter = vitodensAdapter;
        this.automaticPolicy = automaticPolicy;
        this.manualPolicy = manualPolicy;
    }

    /**
     * Winter schedule: Runs during winter months (Nov-Feb) within configured hours.
     * Configurable via heatingctl.winter-cron and heatingctl.winter-enabled.
     */
    @Scheduled(cron = "{heatingctl.winter-cron}", skipExecutionIf = WinterDisabledPredicate.class, concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void controlHeatingWinter() {
        log.debug("Winter schedule triggered");
        controlAutomaticHeating();
    }

    /**
     * Spring schedule: Runs during spring months (Mar-Apr) within configured hours.
     * Configurable via heatingctl.spring-cron and heatingctl.spring-enabled.
     */
    @Scheduled(cron = "{heatingctl.spring-cron}", skipExecutionIf = SpringDisabledPredicate.class, concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void controlHeatingSpring() {
        log.debug("Spring schedule triggered");
        controlAutomaticHeating();
    }

    /**
     * Autumn schedule: Runs during autumn months (Sep-Oct) within configured hours.
     * Configurable via heatingctl.autumn-cron and heatingctl.autumn-enabled.
     */
    @Scheduled(cron = "{heatingctl.autumn-cron}", skipExecutionIf = AutumnDisabledPredicate.class, concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void controlHeatingAutumn() {
        log.debug("Autumn schedule triggered");
        controlAutomaticHeating();
    }

    /**
     * Summer schedule: Runs during summer months (May-Aug) within configured hours.
     * Configurable via heatingctl.summer-cron and heatingctl.summer-enabled.
     */
    @Scheduled(cron = "{heatingctl.summer-cron}", skipExecutionIf = SummerDisabledPredicate.class, concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void controlHeatingSummer() {
        log.debug("Summer schedule triggered");
        controlAutomaticHeating();
    }

    /**
     * Drives manual water heating mode: reads current device states, asks
     * {@link ManualWaterHeatingPolicy} for a decision, and executes it. Does
     * nothing while manual mode is inactive. Runs independently of the seasonal
     * automatic-control schedules above, but shares their {@code synchronized}
     * lock so an automatic and a manual cycle never execute concurrently.
     */
    @Scheduled(every = "50s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public synchronized void controlManualHeating() {
        if (!manualPolicy.getState().active()) {
            return;
        }

        try {
            BatteryStatus batteryStatus = e3dcAdapter.readStatus();
            HeatingRodStatus rodStatus = elwa2Adapter.readStatus();
            GasHeatingStatus gasStatus = vitodensAdapter.readStatus();

            HeatingDecision decision = manualPolicy.decide(rodStatus, batteryStatus, gasStatus);
            applyManualDecision(decision);

            if (decision.completed()) {
                manualPolicy.stop();
                log.info("Manual water heating completed: {}", decision.reason());
            }

        } catch (RuntimeException ex) {
            log.error("Manual water-heating cycle failed", ex);
            deactivateManualHeatingInternal("control failure");
        }
    }

    private void applyManualDecision(HeatingDecision decision) {
        Power limitedPower = decision.power().isGreaterThan(config.maxHeatingPower())
                ? Power.ofWatts(config.maxHeatingPower())
                : decision.power().atLeast(Power.ZERO);
        elwa2Adapter.adjustHeating(limitedPower);

        switch (decision.gasCommand()) {
            case ACTIVATE -> vitodensAdapter.activateHeating();
            case DEACTIVATE -> vitodensAdapter.deactivateHeating();
            case UNCHANGED -> {
                // No gas-state transition requested by the policy.
            }
        }
    }

    private void deactivateManualHeatingInternal(String reason) {
        boolean gasWasOwned = manualPolicy.stop();
        stopHeatingRodBestEffort();

        // isHeatingRequested covers a failed DEACTIVATE transition after the policy
        // already changed its internal state.
        if (gasWasOwned || vitodensAdapter.isHeatingRequested()) {
            try {
                vitodensAdapter.deactivateHeating();
            } catch (RuntimeException ex) {
                log.error("Could not deactivate Vitodens while ending manual mode", ex);
            }
        }
        log.info("Manual water heating ended: {}", reason);
    }

    private void stopHeatingRodBestEffort() {
        try {
            elwa2Adapter.stopHeating();
        } catch (RuntimeException ex) {
            log.error("Could not stop ELWA2", ex);
        }
    }

    private void applyAutomaticDecision(HeatingDecision decision) {
        Power limitedPower = decision.power().isGreaterThan(config.maxHeatingPower())
                ? Power.ofWatts(config.maxHeatingPower())
                : decision.power().atLeast(Power.ZERO);
        elwa2Adapter.adjustHeating(limitedPower);
    }

    /**
     * Internal heating control logic called by seasonal schedulers.
     * Checks solar surplus and temperature to control the heating rod.
     */
    private synchronized void controlAutomaticHeating() {
        if (!shouldControlHeating()) {
            return;
        }

        try {
            HeatingRodStatus rodStatus = elwa2Adapter.readStatus();
            BatteryStatus batteryStatus = e3dcAdapter.readStatus();
            HeatingDecision decision = automaticPolicy.decide(rodStatus, batteryStatus, isBatteryPriorityActive());

            applyAutomaticDecision(decision);

        } catch (RuntimeException ex) {
            log.error("Automatic water-heating cycle failed", ex);
            stopHeatingRodBestEffort();
        }
    }

    /**
     * Checks if heating control should run based on configuration.
     *
     * @return true if heating control should proceed
     */
    private boolean shouldControlHeating() {
        if (!config.enabled()) {
            log.debug("Automatic heating control is disabled.");
        }
        if (manualPolicy.getState().active()) {
            log.debug("Automatic heating control is disabled, because manual heating control is active.");
        }
        return config.enabled() && !manualPolicy.getState().active();
    }

    /**
     * Disables battery priority via runtime override until midnight.
     *
     * @throws IllegalStateException if battery priority is not enabled in
     *                               configuration
     */
    public void disableBatteryPriorityOverride() {
        requireBatteryPriorityEnabled();
        batteryPriorityRuntimeOverride.set(true);
        log.info("Battery priority DISABLED via runtime override (will reset at midnight)");
    }

    public synchronized void activateManualHeating() {
        if (!manualPolicy.start()) {
            return;
        }

        automaticPolicy.reset();
        try {
            // Revoke the previous automatic request immediately.
            elwa2Adapter.stopHeating();
            controlManualHeating();
        } catch (RuntimeException ex) {
            deactivateManualHeatingInternal("activation failure");
            throw ex;
        }
    }

    public synchronized void deactivateManualHeating() {
        deactivateManualHeatingInternal("requested by user");
    }

    /**
     * Re-enables battery priority, clearing any runtime override.
     *
     * @throws IllegalStateException if battery priority is not enabled in
     *                               configuration
     */
    public void enableBatteryPriorityOverride() {
        requireBatteryPriorityEnabled();
        batteryPriorityRuntimeOverride.set(false);
        log.info("Battery priority ENABLED via runtime override");
    }

    private void requireBatteryPriorityEnabled() {
        if (!config.batteryPriorityEnabled()) {
            log.warn("Cannot set battery priority override: Battery priority feature is disabled in configuration");
            throw new IllegalStateException(
                    "Battery priority feature is disabled in configuration (heatingctl.battery-priority-enabled=false). "
                            + "Enable it in configuration before using runtime override.");
        }
    }

    /**
     * Gets the current state of battery priority (considering both config and
     * runtime override).
     *
     * @return true if battery priority is active, false if disabled
     */
    public boolean isBatteryPriorityActive() {
        return config.batteryPriorityEnabled() && !batteryPriorityRuntimeOverride.get();
    }

    /**
     * Resets the battery priority override at midnight.
     * Runs every day at midnight to re-enable battery priority.
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void resetBatteryPriorityOverride() {
        if (batteryPriorityRuntimeOverride.compareAndSet(true, false)) {
            log.info("Midnight reset: Re-enabling battery priority");
        }
    }

    /**
     * Gets the current state of manual mode.
     *
     * @return true if manual mode is active, false if automatic control is active
     */
    public boolean isManualModeActive() {
        return manualPolicy.getState().active();
    }

    public ManualHeatingState getManualStatus() {
        return manualPolicy.getState();
    }

    /**
     * Gets the current season based on the current date.
     *
     * @return the current season
     */
    public Season getCurrentSeason() {
        return Season.current();
    }

    /**
     * Checks if the current season's schedule is enabled in configuration.
     *
     * @return true if the current season is enabled, false otherwise
     */
    public boolean isCurrentSeasonEnabled() {
        return switch (getCurrentSeason()) {
            case WINTER -> config.winterEnabled();
            case SPRING -> config.springEnabled();
            case SUMMER -> config.summerEnabled();
            case AUTUMN -> config.autumnEnabled();
        };
    }
}
