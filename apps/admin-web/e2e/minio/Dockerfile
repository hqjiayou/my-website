# MinIO server for the E2E stack.
# The official container images are no longer anonymously pullable
# (Docker Hub repo removed; quay.io repo is private), so the E2E stack
# builds a minimal image from the official release binary instead.
FROM alpine:3.20

RUN apk add --no-cache curl ca-certificates \
  && curl -fsSL https://dl.min.io/server/minio/release/linux-amd64/minio -o /usr/local/bin/minio \
  && chmod +x /usr/local/bin/minio

EXPOSE 9000
ENTRYPOINT ["minio"]
