.DEFAULT_GOAL := help
.PHONY: help build test verify run

help:    ## List the available targets
	@grep -E '^[a-z]+:.*##' $(MAKEFILE_LIST) | sed 's/:.*##/ -/'

build:   ## Build the Cucumber+ plugin
	./gradlew :plugin-tzatziki:buildPlugin

test:    ## Run the tests (incl. ReflectionApiTest)
	./gradlew test

verify:  ## Plugin Verifier - must stay free of INTERNAL_API_USAGES
	./gradlew :plugin-tzatziki:verifyPlugin

run:     ## Start the IntelliJ sandbox with the plugin
	./gradlew :plugin-tzatziki:runIde
