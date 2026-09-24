.PHONY: local prod

local:
	mvn spring-boot:run -Dspring-boot.run.profiles=local

prod:
	mvn spring-boot:run -Dspring-boot.run.profiles=prod
