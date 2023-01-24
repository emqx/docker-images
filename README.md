# Collection of utility docker images

To add an image:
- create corresponding directory with Dockerfile
- update [main.yaml](./.github/workflows/main.yaml)

There is also a generic `Makefile` for local usage:

```
make
make <image>
make <image>-push
```
