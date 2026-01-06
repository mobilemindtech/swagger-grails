package swagger

import grails.swagger.GSchema
import grails.validation.Validateable
// Importações atualizadas para V3
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.Parameters
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Custom Publisher", description = "Operações para Publisher")
class PublisherController {

    def index() {
        render(status: 200, text: "OK")
    }

    def show(Long id) {
        render(status: 200, text: "OK")
    }

    @ApiResponses([
            @ApiResponse(responseCode = "400", description = "Good"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    ])
    def save(PublisherCommand publisherCommand) {
        render(status: 200, text: "OK")
    }

    @Operation(summary = "Update Publisher", operationId = "updatePublisher")
    def update(Long id, PublisherCommand publisherCommand) {
        render(status: 200, text: "OK")
    }

    @Operation(summary = "Patch Publisher")
    @Parameters([
            @Parameter(name = "arg1", in = ParameterIn.QUERY, required = false, description = "First Argument", schema = @Schema(type = "string")),
            @Parameter(name = "arg2", in = ParameterIn.QUERY, required = false, description = "Second Argument", schema = @Schema(type = "string"))
    ])
    @RequestBody(
            description = "Publishers Command",
            required = true,
            content = @Content(schema = @Schema(implementation = PublisherCommand.class))
    )
    def patch(String id, PublisherCommand publisherCommand) {
        render(status: 200, text: "OK")
    }
}

// Atualize também o Command para ser reconhecido pelo Swagger
@Schema(description = "Comando para criação/edição de Publisher")
class PublisherCommand implements Validateable {

    @GSchema
    @Schema(description = "Lista de autores",
            implementation = AuthorCommand.class)
    List<AuthorCommand> authorCommands
}