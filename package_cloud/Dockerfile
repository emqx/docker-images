FROM ruby:latest

RUN gem install package_cloud
COPY ./entrypoint.sh /entrypoint.sh
ENTRYPOINT ["/entrypoint.sh"]
