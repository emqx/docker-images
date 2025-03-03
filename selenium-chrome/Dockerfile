FROM python:3.12-slim-bookworm

# Install dependencies
RUN apt-get update && \
    apt-get install -y wget unzip gnupg curl && \
    # Download and install specific Chrome version
    wget -q https://dl.google.com/linux/chrome/deb/pool/main/g/google-chrome-stable/google-chrome-stable_133.0.6943.141-1_amd64.deb && \
    apt-get install -y ./google-chrome-stable_133.0.6943.141-1_amd64.deb && \
    rm google-chrome-stable_133.0.6943.141-1_amd64.deb && \
    # Download and install the matching ChromeDriver version
    wget -q -O /tmp/chromedriver.zip https://storage.googleapis.com/chrome-for-testing-public/133.0.6943.141/linux64/chromedriver-linux64.zip && \
    unzip /tmp/chromedriver.zip -d /tmp && \
    mv /tmp/chromedriver-linux64/chromedriver /usr/bin/chromedriver && \
    chmod +x /usr/bin/chromedriver && \
    # Install Python packages
    pip install selenium==4.28.0 pytest==8.3.5 requests==2.32.3 && \
    # Clean up
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

WORKDIR /app
