package swagger.grails

import com.fasterxml.jackson.databind.type.TypeFactory
import groovy.util.logging.Slf4j
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.oas.models.media.Schema

import java.lang.reflect.Field
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Slf4j
class OnlySchemaAnnotationFilter implements ModelConverter {

    @Override
    Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {

        // O TypeFactory resolve tipos como SimpleType, CollectionType, etc para a Classe Java real
        def clazz = TypeFactory.defaultInstance().constructType(type.type).getRawClass()

        if(!isSimpleType(clazz) && !clazz.isAnnotationPresent(io.swagger.v3.oas.annotations.media.Schema)){
            return null
        }

        if (chain.hasNext()) {
            return chain.next().resolve(type, context, chain)
        }
        return null
    }

    /**
     * Método auxiliar para encontrar o campo mesmo em classes herdadas
     */
    private Field findField(Class clazz, String fieldName) {
        try {
            return clazz.getDeclaredField(fieldName)
        } catch (NoSuchFieldException e) {
            if (clazz.superclass && clazz.superclass != Object) {
                return findField(clazz.superclass, fieldName)
            }
        }
        return null
    }

    private boolean isSimpleType(Class clazz){
        def types = [
                List,
                Map,
                Enum,
                String,
                Short,
                Integer,
                Long,
                Float,
                Double,
                Date,
                LocalDate,
                LocalTime,
                LocalDateTime,
                Boolean
        ]
        types.any { clazz.isAssignableFrom(it) }
    }

}
