package io.github.hanbernate.jsonbom.spring;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.reactivestreams.Publisher;
import org.springaicommunity.mcp.method.tool.AsyncMcpToolMethodCallback;
import org.springaicommunity.mcp.method.tool.AsyncStatelessMcpToolMethodCallback;
import org.springaicommunity.mcp.method.tool.ReturnMode;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.model.ModelOptionsUtils;
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
import org.springframework.web.method.HandlerMethod;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.ResolvedTypeWithMembers;
import com.fasterxml.classmate.members.ResolvedField;
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
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessAsyncServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

public class JsonBomMCPBeanPostProcesser  implements BeanPostProcessor{
    McpServerWrapper mcpServer;

    List<HandlerMethod> handlerMethods = new CopyOnWriteArrayList <>();

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
            ResolvedType resolvedType = context.getTypeContext().resolve(responseType);
            ResolvedTypeWithMembers typeWithMembers = context.getTypeContext().resolveWithMembers(resolvedType);
            for(ResolvedField field : typeWithMembers.getMemberFields()){
                String name = field.getName();
                properties.set(name, createByField(context.getTypeContext().createFieldScope(field, typeWithMembers), context));
            }
            objectNode.put("description", context.getGeneratorConfig().resolveDescription(fieldScope));
            return new CustomPropertyDefinition(objectNode);
        }

        private ObjectNode createByField(FieldScope fieldScope, SchemaGenerationContext context){
            var objectMapper = context.getGeneratorConfig().getObjectMapper();
            ObjectNode r = objectMapper.createObjectNode();
            Type type = fieldScope.getRawMember().getGenericType();
            if(isNested(type)){
                ObjectNode properties = createNestedType(type, context);
                r.put("type", "object");
                r.set("properties", properties);
            }else{
                r.put("type", "string");
            }
            String description = context.getGeneratorConfig().resolveDescription(fieldScope);
            if(null != description){
                r.put("description", description);
            }
            return r;
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

        private ObjectNode createNestedType(Type type, SchemaGenerationContext context){
            ObjectNode properties = context.getGeneratorConfig().createObjectNode();
            ResolvedType resolvedType = context.getTypeContext().resolve(type);
            ResolvedTypeWithMembers typeWithMembers = context.getTypeContext().resolveWithMembers(resolvedType);
            for(ResolvedField field : typeWithMembers.getMemberFields()){
                String name = field.getName();
                properties.set(name, createByField(context.getTypeContext().createFieldScope(field, typeWithMembers), context));
            }
            return properties;
        }

    }
    public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {

        McpServerWrapper mcpServer = createWrapperWhenMcpServer(bean);

        if(mcpServer != null){
            this.mcpServer = mcpServer;
            this.mcpServer.addHandlerMethods(handlerMethods);
        }

        List<HandlerMethod> handlerMethods = Stream
            .of(ReflectionUtils.getDeclaredMethods(
                    AopUtils.isAopProxy(bean) ? AopUtils.getTargetClass(bean) : bean.getClass()))
            .filter(this::isToolAnnotatedMethod)
            .filter(toolMethod -> !isFunctionalType(toolMethod))
            .filter(ReflectionUtils.USER_DECLARED_METHODS::matches)
            .map(toolMethod -> new HandlerMethod(bean, toolMethod))
            .collect(Collectors.toList());
        this.handlerMethods.addAll(handlerMethods);
        if(this.mcpServer != null){
            this.mcpServer.addHandlerMethods(handlerMethods);
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

    private ToolCallback buildSyncToolCallback(HandlerMethod handlerMethod){
        return MethodToolCallback.builder()
					.toolDefinition(buildToolDefinition(handlerMethod.getMethod()))
					.toolMetadata(ToolMetadata.from(handlerMethod.getMethod()))
					.toolMethod(handlerMethod.getMethod())
					.toolObject(handlerMethod.getBean())
					.toolCallResultConverter(ToolUtils.getToolCallResultConverter(handlerMethod.getMethod()))
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
        void addHandlerMethods(List<HandlerMethod> handlerMethods);
    }

    private class McpSyncServerWrapper implements McpServerWrapper{
        McpSyncServer server;
        public McpSyncServerWrapper(McpSyncServer server){
            this.server = server;
        }

        @Override
        public void addHandlerMethods(List<HandlerMethod> handlerMethods) {
            for(HandlerMethod hm : handlerMethods){
                McpServerFeatures.SyncToolSpecification specification = McpToolUtils.toSyncToolSpecification(buildSyncToolCallback(hm));
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
        public void addHandlerMethods(List<HandlerMethod> handlerMethods) {
            for(HandlerMethod hm : handlerMethods){
                ToolDefinition definition = buildToolDefinition(hm.getMethod());
                McpSchema.Tool tool = McpSchema.Tool.builder()
                    .name(definition.name())
                    .description(definition.description())
                    .inputSchema(ModelOptionsUtils.jsonToObject(definition.inputSchema(),
                            McpSchema.JsonSchema.class))
                    .build();
                AsyncMcpToolMethodCallback callHandler = new AsyncMcpToolMethodCallback(parseReturnMode(hm), hm.getMethod(), hm.getBean());
                McpServerFeatures.AsyncToolSpecification specification = McpServerFeatures.AsyncToolSpecification.builder()
                    .tool(tool)
                    .callHandler(callHandler)
                    .build();
                this.server.addTool(specification).block(); //初始化的时候影响不大
            }
        }
    }

    private class McpStatelessSyncServerWrapper implements McpServerWrapper{
        McpStatelessSyncServer server;
        public McpStatelessSyncServerWrapper(McpStatelessSyncServer server){
            this.server = server;
        }

        @Override
        public void addHandlerMethods(List<HandlerMethod> handlerMethods) {
            for(HandlerMethod hm : handlerMethods){
                McpStatelessServerFeatures.SyncToolSpecification specification = McpToolUtils.toStatelessSyncToolSpecification(buildSyncToolCallback(hm), MimeTypeUtils.APPLICATION_JSON);
                this.server.addTool(specification);
            }
        }
    }

    private ReturnMode parseReturnMode(HandlerMethod hm ){
        Class<?> returnType = hm.getMethod().getReturnType();
        if(String.class.equals(returnType)){
            return ReturnMode.TEXT;
        }

        if(void.class == returnType){
            return ReturnMode.VOID;
        }

        return ReturnMode.STRUCTURED;
    }

    private class McpStatelessAsyncServerWrapper implements McpServerWrapper{
        McpStatelessAsyncServer server;
        public McpStatelessAsyncServerWrapper(McpStatelessAsyncServer server){
            this.server = server;
        }

        @Override
        public void addHandlerMethods(List<HandlerMethod> handlerMethods) {
            for(HandlerMethod hm : handlerMethods){
                ToolDefinition definition = buildToolDefinition(hm.getMethod());
                McpSchema.Tool tool = McpSchema.Tool.builder()
                    .name(definition.name())
                    .description(definition.description())
                    .inputSchema(ModelOptionsUtils.jsonToObject(definition.inputSchema(),
                            McpSchema.JsonSchema.class))
                    .build();
                AsyncStatelessMcpToolMethodCallback callHandler = new AsyncStatelessMcpToolMethodCallback(parseReturnMode(hm), hm.getMethod(), hm.getBean());
                McpStatelessServerFeatures.AsyncToolSpecification specification = McpStatelessServerFeatures.AsyncToolSpecification.builder()
                    .tool(tool)
                    .callHandler(callHandler)
                    .build();
                this.server.addTool(specification).block();//初始化的时候影响不大
            }
        }
    }
}
