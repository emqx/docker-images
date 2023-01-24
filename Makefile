IMAGES = $(foreach df,$(wildcard */Dockerfile),$(df:%/Dockerfile=%))

.PHONY: all
all: $(IMAGES)

.PHONY: $(IMAGES)
define gen-build-image-target
$1:
	@docker build -t ghcr.io/emqx/$1 $1
endef
$(foreach img,$(IMAGES),$(eval $(call gen-build-image-target,$(img))))

.PHONY: $(IMAGES:%=%-push)
define gen-push-image-target
$1-push:
	@docker push ghcr.io/emqx/$1
endef
$(foreach img,$(IMAGES),$(eval $(call gen-push-image-target,$(img))))
