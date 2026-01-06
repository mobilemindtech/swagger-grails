
                    // Grails groovy compilation configuration to ensure ASTs are applied correctly
                    
                    withConfig(configuration) {
                inline(phase: 'CONVERSION') { source, context, classNode ->
                    classNode.putNodeMetaData('projectVersion', '0.6.0')
                    classNode.putNodeMetaData('projectName', 'swagger-grails')
                    classNode.putNodeMetaData('isPlugin', 'true')
                }
            }

                    
                