package swagger

import grails.swagger.GSchema
import grails.validation.Validateable
import io.swagger.v3.oas.annotations.media.Schema

class AuthorController {
    def index() {
        render(status: 200, text: "OK")
    }

    def save(AuthorCommand authorCommand) {
        render(status: 200, text: "OK")
    }

    def show(String id) {
        render(status: 200, text: "OK")
    }

    def delete(String id) {
        render(status: 200, text: "OK")
    }

    def update(String id) {
        render(status: 200, text: "OK")
    }

    def patch(String id) {
        render(status: 200, text: "OK")
    }

    def custom(String name, int age) {
        render(status: 200, text: "OK")
    }
}

@Schema(description = "Author")
class AuthorCommand implements Validateable {
    @GSchema
    @Schema(description = "Nome")
    String name

    @Schema(description = "Age")
    int age

}
