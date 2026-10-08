FROM python:3.12-slim-bookworm

# Install dependencies
RUN apt-get update && \
    apt-get install -y wget unzip gnupg curl && \
    # Install the current stable Chrome. Google's apt pool keeps only recent
    # builds, so a pinned .deb URL stops resolving a few releases later.
    wget -q -O /tmp/chrome.deb https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb && \
    apt-get install -y /tmp/chrome.deb && \
    # Install the ChromeDriver matching the Chrome that was just installed.
    CHROME_VERSION="$(google-chrome --version | grep -oE '[0-9]+(\.[0-9]+){3}')" && \
    if ! wget -q -O /tmp/chromedriver.zip "https://storage.googleapis.com/chrome-for-testing-public/${CHROME_VERSION}/linux64/chromedriver-linux64.zip"; then \
        DRIVER_URL="$(wget -qO- https://googlechromelabs.github.io/chrome-for-testing/last-known-good-versions-with-downloads.json | \
            python3 -c 'import json,sys; print([d["url"] for d in json.load(sys.stdin)["channels"]["Stable"]["downloads"]["chromedriver"] if d["platform"]=="linux64"][0])')" && \
        wget -q -O /tmp/chromedriver.zip "$DRIVER_URL"; \
    fi && \
    unzip /tmp/chromedriver.zip -d /tmp && \
    mv /tmp/chromedriver-linux64/chromedriver /usr/bin/chromedriver && \
    chmod +x /usr/bin/chromedriver && \
    # Install Python packages
    pip install selenium==4.28.0 pytest==8.3.5 requests==2.32.3 && \
    # Clean up
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

WORKDIR /app
