FROM python:3.12.4-slim

RUN apt-get update && \
    apt-get install -y wget chromium chromium-driver && \
    pip install selenium==4.22.0 pytest==8.2.2 requests==2.32.3 && \
    rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

WORKDIR /app
