FROM python:3.11.2-alpine
ARG GITLINT_VERSION=0.19.1

RUN apk add git
RUN pip install gitlint==$GITLINT_VERSION

RUN git config --global --add safe.directory /repo
ENTRYPOINT ["gitlint", "--target", "/repo"]
