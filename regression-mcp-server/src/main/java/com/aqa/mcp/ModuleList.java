package com.aqa.mcp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

record ModuleList(List<ModuleDescriptor> modules) {

    private static final String POM_ERROR = "POM_ERROR";

    ModuleList {
        modules = List.copyOf(modules);
    }

    static ModuleList forRoot(RepositoryRoot repositoryRoot) {
        Path root = repositoryRoot.path();
        Set<Path> encounteredPaths = new HashSet<>();
        List<ModuleDescriptor> modules = new ArrayList<>();
        for (String declaration : readDeclaredModules(root.resolve("pom.xml"))) {
            Path resolvedPath = root.resolve(modulePath(declaration)).normalize();
            if (!resolvedPath.startsWith(root)) {
                throw pomError("Module path escapes REGRESSION_ROOT: " + declaration);
            }
            if (!encounteredPaths.add(resolvedPath)) {
                throw pomError("Duplicate module declaration: " + declaration);
            }
            rejectExistingSymlinkEscape(root, resolvedPath, declaration);
            Path relativePath = root.relativize(resolvedPath);
            if (relativePath.getNameCount() == 0) {
                throw pomError("Module path must identify a child of REGRESSION_ROOT: " + declaration);
            }
            String name = relativePath.getFileName().toString();
            modules.add(new ModuleDescriptor(name, display(relativePath), ModuleTypeClassifier.classify(name),
                    Files.isDirectory(resolvedPath), Files.isRegularFile(resolvedPath.resolve("pom.xml"))));
        }
        return new ModuleList(modules);
    }

    Map<String, Object> asToolOutput() {
        return Map.of("status", "ok", "data", Map.of("modules", modules.stream()
                .map(ModuleDescriptor::asToolOutput).toList()));
    }

    private static List<String> readDeclaredModules(Path pomPath) {
        final Document document;
        try {
            document = secureDocumentBuilder().parse(pomPath.toFile());
        }
        catch (SAXException | IOException exception) {
            throw new RepositoryInspectionException(POM_ERROR, "Unable to parse root pom.xml.", exception);
        }
        Element project = document.getDocumentElement();
        if (project == null || !"project".equals(project.getLocalName())) {
            throw pomError("Root pom.xml must contain a project element.");
        }
        List<String> modules = new ArrayList<>();
        for (Element modulesElement : directChildElements(project, "modules")) {
            for (Element moduleElement : directChildElements(modulesElement, "module")) {
                String declaration = moduleElement.getTextContent().trim();
                if (declaration.isEmpty()) {
                    throw pomError("Module declaration must not be blank.");
                }
                modules.add(declaration);
            }
        }
        return modules;
    }

    private static DocumentBuilder secureDocumentBuilder() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder();
        }
        catch (ParserConfigurationException exception) {
            throw new RepositoryInspectionException(POM_ERROR, "Unable to configure secure XML parsing.", exception);
        }
    }

    private static List<Element> directChildElements(Element parent, String localName) {
        List<Element> matches = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child.getNodeType() == Node.ELEMENT_NODE && localName.equals(child.getLocalName())) {
                matches.add((Element) child);
            }
        }
        return matches;
    }

    private static Path modulePath(String declaration) {
        try {
            Path path = Path.of(declaration);
            if (path.isAbsolute()) {
                throw pomError("Module path must be relative: " + declaration);
            }
            return path;
        }
        catch (InvalidPathException exception) {
            throw new RepositoryInspectionException(POM_ERROR, "Invalid module path: " + declaration, exception);
        }
    }

    private static void rejectExistingSymlinkEscape(Path root, Path resolvedPath, String declaration) {
        if (!Files.exists(resolvedPath)) {
            return;
        }
        try {
            if (!resolvedPath.toRealPath().startsWith(root)) {
                throw pomError("Module path escapes REGRESSION_ROOT: " + declaration);
            }
        }
        catch (IOException exception) {
            throw new RepositoryInspectionException(POM_ERROR, "Unable to resolve module path: " + declaration, exception);
        }
    }

    private static String display(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static RepositoryInspectionException pomError(String message) {
        return new RepositoryInspectionException(POM_ERROR, message);
    }
}

record ModuleDescriptor(String name, String relativePath, ModuleType type, boolean directoryExists, boolean pomExists) {

    Map<String, Object> asToolOutput() {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("name", name);
        output.put("relativePath", relativePath);
        output.put("type", type.name());
        output.put("directoryExists", directoryExists);
        output.put("pomExists", pomExists);
        return Map.copyOf(output);
    }
}
