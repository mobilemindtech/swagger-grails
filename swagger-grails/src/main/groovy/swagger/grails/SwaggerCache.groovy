package swagger.grails

import groovy.util.logging.Slf4j
import io.swagger.v3.oas.models.OpenAPI
import org.codehaus.groovy.runtime.InvokerHelper

/**
 * Cache atualizado para OpenAPI 3 (V3) compatível com Java 24.
 * Substitui o modelo antigo 'Swagger' pelo novo 'OpenAPI'.
 */
@Slf4j
class SwaggerCache {
    // Agora utilizamos o modelo OpenAPI 3
    OpenAPI openApi

    private OpenAPI backup = null
    private boolean flushed = true

    /**
     * Retorna a instância já construída do OpenAPI se existir.
     * Caso contrário, invoca o closure para reconstruir a especificação.
     *
     * @param closure
     * @return Instância de {@link OpenAPI}
     */
    OpenAPI getOrElse(Closure closure) {
        // Inicializa o backup se for a primeira execução
        if (!backup) {
            backup = new OpenAPI()
            // No Java 24, preferimos copiar propriedades básicas ou
            // resetar o objeto para evitar problemas de reflexão profunda
            InvokerHelper.setProperties(backup, openApi.properties)
        }

        if (flushed) {
            log.info "SwaggerCache: detectada mudança ou cache limpo. Reconstruindo especificação..."

            // Restaura o estado base antes de re-escanear
            InvokerHelper.setProperties(openApi, backup.properties)

            try {
                // Invoca o Reader.read() passando o objeto openApi
                closure.call(openApi)
                backup = openApi
            } catch (Exception e) {
                log.error("Erro ao gerar especificação OpenAPI: ${e.message}", e)
                openApi = backup
            } finally {
                flushed = false
            }
        }
        openApi
    }

    /**
     * Marca o cache como sujo para forçar a reconstrução na próxima requisição.
     */
    void flush() {
        flushed = true
    }
}