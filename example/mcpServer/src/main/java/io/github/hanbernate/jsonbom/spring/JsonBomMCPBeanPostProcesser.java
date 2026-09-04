package io.github.hanbernate.jsonbom.spring;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import io.github.hanbernate.jsonbom.victools.BomPropertyDefinitionProvider;
import org.springframework.ai.mcp.annotation.method.tool.AsyncMcpToolMethodCallback;
import org.springframework.ai.mcp.annotation.method.tool.AsyncStatelessMcpToolMethodCallback;
import org.springframework.ai.mcp.annotation.method.tool.ReturnMode;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.support.ToolUtils;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.ai.util.json.schema.SpringAiSchemaModule;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.NonNull;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.method.HandlerMethod;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import com.github.victools.jsonschema.module.jackson.JacksonSchemaModule;
import com.github.victools.jsonschema.module.swagger2.Swagger2Module;
import com.github.victools.jsonschema.generator.Module;
import com.github.victools.jsonschema.generator.Option;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessAsyncServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public class JsonBomMCPBeanPostProcesser  implements BeanPostProcessor{
    McpServerWrapper mcpServer;

    List<HandlerMethod> handlerMethods = new CopyOnWriteArrayList <>();

    SchemaGenerator generator;

    public JsonBomMCPBeanPostProcesser(){
		Module jacksonModule = new JacksonSchemaModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED);
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

    private JsonMapper jsonMapper = JacksonUtils.getDefaultJsonMapper();

    private McpSchema.Tool buildTool(Method method){
        ObjectNode root = jsonMapper.createObjectNode();
        root.put("type", "object");

        ArrayNode required = root.arrayNode();
        Parameter[] parameters = method.getParameters();
        ObjectNode properties = jsonMapper.createObjectNode();
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
        return McpSchema.Tool.builder(ToolUtils.getToolName(method), new JacksonMcpJsonMapper(jsonMapper),root.toPrettyString())
                    .description(ToolUtils.getToolDescription(method))
                    .build();
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

    private McpServerWrapper createWrapperWhenMcpServer(Object bean){
        if(bean instanceof McpAsyncServer){
            return new McpAsyncServerWrapper((McpAsyncServer) bean);
        }
        if(bean instanceof McpStatelessAsyncServer){
            return new McpStatelessAsyncServerWrapper((McpStatelessAsyncServer) bean);
        }
        return null;
    }

    private interface McpServerWrapper{
        void addHandlerMethods(List<HandlerMethod> handlerMethods);
    }

    private class McpAsyncServerWrapper implements McpServerWrapper{
        McpAsyncServer server;
        public McpAsyncServerWrapper(McpAsyncServer server){
            this.server = server;
        }

        @Override
        public void addHandlerMethods(List<HandlerMethod> handlerMethods) {
            for(HandlerMethod hm : handlerMethods){
                McpSchema.Tool tool = buildTool(hm.getMethod());
                AsyncMcpToolMethodCallback callHandler = new AsyncMcpToolMethodCallback(parseReturnMode(hm), hm.getMethod(), hm.getBean());
                McpServerFeatures.AsyncToolSpecification specification = McpServerFeatures.AsyncToolSpecification.builder()
                    .tool(tool)
                    .callHandler(callHandler)
                    .build();
                this.server.addTool(specification).block(); //初始化的时候影响不大
            }
        }
    }

    private class McpStatelessAsyncServerWrapper implements McpServerWrapper{
        McpStatelessAsyncServer server;
        public McpStatelessAsyncServerWrapper(McpStatelessAsyncServer server){
            this.server = server;
        }

        @Override
        public void addHandlerMethods(List<HandlerMethod> handlerMethods) {
            for(HandlerMethod hm : handlerMethods){
                McpSchema.Tool tool = buildTool(hm.getMethod());
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
