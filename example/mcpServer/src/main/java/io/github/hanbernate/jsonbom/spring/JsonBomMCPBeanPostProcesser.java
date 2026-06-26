package io.github.hanbernate.jsonbom.spring;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.support.ToolUtils;
import org.springframework.ai.util.json.schema.SpringAiSchemaModule;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.NonNull;
import org.springframework.util.ClassUtils;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.ReflectionUtils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.CustomPropertyDefinition;
import com.github.victools.jsonschema.generator.CustomPropertyDefinitionProvider;
import com.github.victools.jsonschema.generator.FieldScope;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerationContext;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import com.github.victools.jsonschema.module.swagger2.Swagger2Module;
import com.github.victools.jsonschema.generator.Module;
import com.github.victools.jsonschema.generator.Option;

import io.github.hanbernate.jsonbom.core.BomType;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessAsyncServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.McpSyncServer;

public class JsonBomMCPBeanPostProcesser  implements BeanPostProcessor{
    McpServerWrapper mcpServer;

    List<ToolCallback> callbacks = new CopyOnWriteArrayList <>();

    SchemaGenerator generator;

    public JsonBomMCPBeanPostProcesser(){
		Module jacksonModule = new JacksonModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED);
		Module openApiModule = new Swagger2Module();
		Module springAiSchemaModule = new SpringAiSchemaModule();
        SchemaGeneratorConfigBuilder schemaGeneratorConfigBuilder = new SchemaGeneratorConfigBuilder(
				SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
			.with(jacksonModule)
			.with(openApiModule)
			.with(springAiSchemaModule)
			.with(Option.EXTRA_OPEN_API_FORMAT_VALUES)
			.with(Option.PLAIN_DEFINITION_KEYS)
            .without(Option.SCHEMA_VERSION_INDICATOR);
        schemaGeneratorConfigBuilder.forFields()
            .withCustomDefinitionProvider(new BomPropertyDefinitionProvider());
        this.generator = new SchemaGenerator(schemaGeneratorConfigBuilder.build());
    }

    class BomPropertyDefinitionProvider implements CustomPropertyDefinitionProvider<FieldScope>{

        @Override
        public CustomPropertyDefinition provideCustomSchemaDefinition(FieldScope fieldScope,
                SchemaGenerationContext context) {
            BomType bomType = fieldScope.getAnnotation(BomType.class);
            if(null == bomType){
                return null;
            }
            Class<?> responseType = bomType.value();
            ObjectMapper objectMapper = context.getGeneratorConfig().getObjectMapper();
            ObjectNode objectNode = objectMapper.createObjectNode();
            objectNode.put("type", "object");
            ObjectNode properties = objectNode.putObject("properties");
            for(Field field : responseType.getDeclaredFields()){
                String name = field.getName();
                properties.set(name, createByField(field, objectMapper));
            }
            objectNode.put("description", context.getGeneratorConfig().resolveDescription(fieldScope));
            return new CustomPropertyDefinition(objectNode);
        }

        private ObjectNode createByField(Field field, ObjectMapper objectMapper){
            ObjectNode r = objectMapper.createObjectNode();
            Type type = field.getGenericType();
            if(isNested(type)){
                ObjectNode properties = createNestedType(type, objectMapper);
                r.put("type", "object");
                r.set("properties", properties);
            }else{
                r.put("type", "string");
            }
            String description = getDescrioption(field);
            if(null != description){
                r.put("description", description);
            }
            return r;
        }

        private String getName(Field f){
            JsonProperty jsonProperty = f.getAnnotation(JsonProperty.class);
            return null != jsonProperty ? jsonProperty.value() : f.getName();
        }

        private String getDescrioption(Field field){
            JsonPropertyDescription jsonPropertyDescription = field.getAnnotation(JsonPropertyDescription.class);
            return null != jsonPropertyDescription ? jsonPropertyDescription.value() : null;
        }

        private boolean isNested(Type type){
            Class<?> cls = (Class<?>) type;
            if(cls.isPrimitive()){
                return false;
            }

            if(Number.class.isAssignableFrom(cls) || Character.class == cls || Boolean.class == cls || String.class == cls){
                return false;
            }
            return true;
        }

        private ObjectNode createNestedType(Type type, ObjectMapper objectMapper){
            ObjectNode objectNode = objectMapper.createObjectNode();
            for(Field f : ((Class<?>) type).getDeclaredFields()){
                objectNode.set(getName(f), createByField(f, objectMapper));
            }
            return objectNode;
        }

    }
    public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {

        McpServerWrapper mcpServer = createWrapperWhenMcpServer(bean);

        if(mcpServer != null){
            this.mcpServer = mcpServer;
            this.mcpServer.addToolCallbacks(callbacks);
        }

        List<ToolCallback> callbacks = Stream
            .of(ReflectionUtils.getDeclaredMethods(
                    AopUtils.isAopProxy(bean) ? AopUtils.getTargetClass(bean) : bean.getClass()))
            .filter(this::isToolAnnotatedMethod)
            .filter(toolMethod -> !isFunctionalType(toolMethod))
            .filter(ReflectionUtils.USER_DECLARED_METHODS::matches)
            .map(toolMethod -> buiToolCallback(toolMethod, bean))
            .collect(Collectors.toList());
        this.callbacks.addAll(callbacks);
        if(this.mcpServer != null){
            this.mcpServer.addToolCallbacks(callbacks);
        }
		return bean;
	}

	private boolean isToolAnnotatedMethod(Method method) {
		Tool annotation = AnnotationUtils.findAnnotation(method, Tool.class);
		return Objects.nonNull(annotation);
	}

	private boolean isFunctionalType(Method toolMethod) {
		var isFunction = ClassUtils.isAssignable(Function.class, toolMethod.getReturnType())
				|| ClassUtils.isAssignable(Supplier.class, toolMethod.getReturnType())
				|| ClassUtils.isAssignable(Consumer.class, toolMethod.getReturnType());

		return isFunction;
	}

    private ToolCallback buiToolCallback(Method toolMethod, Object toolObject){
        return MethodToolCallback.builder()
					.toolDefinition(buildToolDefinition(toolMethod))
					.toolMetadata(ToolMetadata.from(toolMethod))
					.toolMethod(toolMethod)
					.toolObject(toolObject)
					.toolCallResultConverter(ToolUtils.getToolCallResultConverter(toolMethod))
					.build();
    }

    private ObjectMapper objectMapper = new ObjectMapper();

    private ToolDefinition buildToolDefinition(Method method){
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");

        ArrayNode required = root.arrayNode();
        Parameter[] parameters = method.getParameters();
        ObjectNode properties = objectMapper.createObjectNode();
        for(Parameter parameter : parameters){
            String parameterName = parameter.getName();
            ToolParam toolParam = parameter.getAnnotation(ToolParam.class);
            
            if(toolParam.required()){
                required.add(parameterName);
            }

            ObjectNode parameterNode = this.generator.generateSchema(parameter.getParameterizedType());
            parameterNode.put("description", toolParam.description());
            properties.set(parameterName, parameterNode);
        }
        root.set("properties", properties);
        if(!required.isEmpty()){
            root.set("required", required);
        };
        return DefaultToolDefinition.builder()
			.name(ToolUtils.getToolName(method))
			.description(ToolUtils.getToolDescription(method))
            //.inputSchema(inputStream)
			.inputSchema(root.toPrettyString())
            .build();
    }

    private McpServerWrapper createWrapperWhenMcpServer(Object bean){
        if(bean instanceof McpSyncServer){
            return new McpSyncServerWrapper((McpSyncServer) bean);
        }
        if(bean instanceof McpAsyncServer){
            return new McpAsyncServerWrapper((McpAsyncServer) bean);
        }
        if(bean instanceof McpStatelessSyncServer){
            return new McpStatelessSyncServerWrapper((McpStatelessSyncServer) bean);
        }
        if(bean instanceof McpStatelessAsyncServer){
            return new McpStatelessAsyncServerWrapper((McpStatelessAsyncServer) bean);
        }
        return null;
    }

    private interface McpServerWrapper{
        void addToolCallbacks(List<ToolCallback> callbacks);
    }

    private class McpSyncServerWrapper implements McpServerWrapper{
        McpSyncServer server;
        public McpSyncServerWrapper(McpSyncServer server){
            this.server = server;
        }

        @Override
        public void addToolCallbacks(List<ToolCallback> callbacks) {
            for(ToolCallback callback : callbacks){
                McpServerFeatures.SyncToolSpecification specification = McpToolUtils.toSyncToolSpecification(callback);
                this.server.addTool(specification);
            }
        }
    }

    private class McpAsyncServerWrapper implements McpServerWrapper{
        McpAsyncServer server;
        public McpAsyncServerWrapper(McpAsyncServer server){
            this.server = server;
        }

        @Override
        public void addToolCallbacks(List<ToolCallback> callbacks) {
            for(ToolCallback callback : callbacks){
                McpServerFeatures.AsyncToolSpecification specification = McpToolUtils.toAsyncToolSpecification(callback);
                this.server.addTool(specification);
            }
        }
    }

    private class McpStatelessSyncServerWrapper implements McpServerWrapper{
        McpStatelessSyncServer server;
        public McpStatelessSyncServerWrapper(McpStatelessSyncServer server){
            this.server = server;
        }

        @Override
        public void addToolCallbacks(List<ToolCallback> callbacks) {
            for(ToolCallback callback : callbacks){
                McpStatelessServerFeatures.SyncToolSpecification specification = McpToolUtils.toStatelessSyncToolSpecification(callback, MimeTypeUtils.APPLICATION_JSON);
                this.server.addTool(specification);
            }
        }
    }

    private class McpStatelessAsyncServerWrapper implements McpServerWrapper{
        McpStatelessAsyncServer server;
        public McpStatelessAsyncServerWrapper(McpStatelessAsyncServer server){
            this.server = server;
        }

        @Override
        public void addToolCallbacks(List<ToolCallback> callbacks) {
            for(ToolCallback callback : callbacks){
                McpStatelessServerFeatures.AsyncToolSpecification specification = McpToolUtils.toStatelessAsyncToolSpecification(callback, MimeTypeUtils.APPLICATION_JSON);
                this.server.addTool(specification);
            }
        }
    }

    
}
