# swagger-grails

Generate Swagger documentation for your **Grails 7+** app.

## Built With

* Java 24
* Grails 7
* Swagger Core V3 2.2.22

## Installation

Add this line to the `repositories` block in your `build.gradle` file:

```groovy
maven { url 'https://raw.githubusercontent.com/mobilemindtech/m2/master' }
```

Add this line to the `dependencies` block in your `build.gradle` file:

```groovy
    implementation 'org.apache.grails:swagger-grails:0.6.0'
    implementation 'org.apache.grails:swagger-grails-ast:0.6.0'
    implementation "io.swagger.core.v3:swagger-annotations-jakarta:2.2.22"

```

## Usage

The following __UrlMappings.groovy__ and __AuthorController.groovy__

```groovy
class UrlMappings {
    static mappings = {
        "/$controller/$action?/$id?(.$format)?" {
            constraints {
                // apply constraints here
            }
        }

        "/authors"(resources: 'author')
    }
}
```

```groovy
class AuthorController {
    def index() {/*...*/ }

    def save(AuthorCommand authorCommand) {/*...*/ }

    def show(String id) {/*...*/ }

    def delete(String id) {/*...*/ }

    def update(String id) {/*...*/ }

    def patch(String id) {/*...*/ }

    def custom(String name, int age) {/*...*/ }
}

class AuthorCommand implements Validateable {
    String name
}
```

will generate Swagger documentation like this :

<p align="center">
    <img src="src/test/resources/author-controller.png?raw=true" />
</p>

This plugin will only generate the JSON representation of your endpoints. You'll need to implement your
own [swagger-ui](https://github.com/swagger-api/swagger-ui) to consume the JSON.

If your __UrlMappings__ file includes the default __"/$controller/$action?/$id?(.$format)?"__ mapping then the JSON will
be accessible by hitting __http://localhost:8080/swagger/api__. This endpoint can be customized by adding __"
/custom/swagger/endpoint"(controller: "swagger", action: "api")__ to your UrlMappings file.

Also, any Swagger annotations that are manually added to an action in a controller, will be used when generating the
Swagger documentation. So you could let the plugin do what it does by default and then enhance the actions with
responses, authorizations, etc.

## Configuration

```yaml
swagger:
  requireAnnotations: true # process only controller with annotations
  info:
    title: My api test
    version: 0.1.1
    description: Server api test
  server:
    uri: https://myservice.test.com/api
  security:
    apiKey:
      enabled: true
      name: apiKey
      global: false
      types:
        - header
        - query
        - cookie
    bearer:
      enabled: true
      name: bearerAuth
      global: false
    basic:
      enabled: true
      name: basicAuth
      global: false 
```

Override openApi bean

```groovy
openApi(OpenAPI) { bean ->
    servers = [new Server().url("https://myserver.io/api").description("My Server API")]
    SecurityScheme apiKeyScheme = new SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .name("bearer")
            .bearerFormat("JWT")
            .scheme("bearer")
            .in(SecurityScheme.In.HEADER)
            .description("Bearer auth")
    components = new Components().addSecuritySchemes("bearer", apiKeyScheme)
    security = [new SecurityRequirement().addList("bearer")]
    info = new Info().title("My API title").version("0.5")
    openapi = "3.0.1"
}
```


## Running the plugin locally


##### Prerequisites

* Java 24
* Grails 7

##### Running

* Clone or download the repo
* Run `./gradlew grails-swagger-example`
* Navigate to `http://localhost:8080/swagger`

