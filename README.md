# Collection of utility docker images

To add an image:
- create corresponding directory with Dockerfile
- Add a `vsn` file in the directory (bump vsn whenver there is a change)
- update [main.yaml](./.github/workflows/main.yaml)

There is also a generic `Makefile` for local usage:

```
make
make <image>
make <image>-push
```
