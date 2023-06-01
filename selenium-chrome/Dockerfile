FROM python:3.10

RUN apt-get update && \
    apt-get install -y wget chromium chromium-driver && \
    pip install selenium pytest && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app
