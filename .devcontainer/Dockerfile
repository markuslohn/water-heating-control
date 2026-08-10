FROM mcr.microsoft.com/devcontainers/base:ubuntu-24.04

USER root

RUN chmod 1777 /tmp \
    && apt-get update \
    && apt-get install -y --no-install-recommends \
    curl \
    traceroute \
    iputils-ping \
    && rm -rf /var/lib/apt/lists/*

