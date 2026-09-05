# Changelog

本文档记录项目所有重要变更，格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)。

## [Unreleased]

### 0.2.0.rc2
- 修复BomPropertyDefinitionProvider在处理泛型时异常，并支持@BomMapping注解；已知问题：全局注册的valueHandler无法识别，会影响mcp的toollist输出；mcp场景下请使用注解方式设置valueNode=true
### 0.2.0.rc1
- 新增MCP支持。使用 BomPropertyDefinitionProvider，配合 victools jsonschema-generator 自动生成带 @BomType 注解字段的 JSON Schema。如何与spring-ai集成可以参考example/mcpServer模块
### 0.1.0
- 新增Jackson3支持
### 0.0.3
- Bom 新增 mergeWithEmpty/mergeOtherBom/clone 方法
### 0.0.2
- 支持通过map或者其他集合类/字符串数组创建Bom
- 新增 createWithEmptyValue 的 String... 变参重载
- 集合字段缺少 @BomMapping genericType 时自动推断泛型参数
- map 方法支持BomModel实现作为 models 参数