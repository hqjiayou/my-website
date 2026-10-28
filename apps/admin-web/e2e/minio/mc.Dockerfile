# MinIO client (mc) for the one-shot bucket-init service.
# Built from the official release binary for the same reason as
# the server image (see Dockerfile in this directory).
FROM alpine:3.20

RUN apk add --no-cache curl ca-certificates \
  && curl -fsSL https://dl.min.io/client/mc/release/linux-amd64/mc -o /usr/local/bin/mc \
  && chmod +x /usr/local/bin/mc

ENTRYPOINT ["mc"]
