package io.github.teemuki8.libgdx.agent.gameplay.core.prefab;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Component;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.ComponentType;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.PrefabId;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Closed, bounded streaming parser for {@code gameplay-prefabs/1}. */
public final class PrefabParser {
    private static final String SCHEMA_VERSION = "gameplay-prefabs/1";
    private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "prefabs");
    private static final Set<String> PREFAB_FIELDS = Set.of("id", "components");

    private final ComponentCodecRegistry codecs;
    private final PrefabLimits limits;
    private final JsonFactory jsonFactory;

    /** Creates a parser with explicit codecs and limits. */
    public PrefabParser(ComponentCodecRegistry codecs, PrefabLimits limits) {
        this.codecs = Objects.requireNonNull(codecs, "codecs");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.jsonFactory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNestingDepth(limits.maxDepth())
                        .maxStringLength(limits.maxStringLength())
                        .maxNumberLength(limits.maxNumberLength())
                        .build())
                .build();
    }

    /** Parses one complete UTF-8 document without resolving external resources. */
    public PrefabCatalog parse(byte[] utf8Json) {
        Objects.requireNonNull(utf8Json, "utf8Json");
        if (utf8Json.length > limits.maxDocumentBytes()) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.PREFAB_INPUT_LIMIT_EXCEEDED,
                    "parse-prefab-document",
                    "at most " + limits.maxDocumentBytes() + " encoded bytes",
                    Integer.toString(utf8Json.length),
                    "Split the catalog or remove unnecessary prefab data.");
        }
        try (JsonParser parser = jsonFactory.createParser(utf8Json)) {
            JsonToken token = parser.nextToken();
            if (token != JsonToken.START_OBJECT) {
                throw tokenFailure(parser, GameplayDiagnosticCode.MALFORMED_PREFAB_JSON,
                        "root JSON object", token);
            }
            PrefabCatalog result = parseRoot(parser);
            if (parser.nextToken() != null) {
                throw tokenFailure(parser, GameplayDiagnosticCode.TRAILING_PREFAB_CONTENT,
                        "end of document", parser.currentToken());
            }
            return result;
        } catch (StreamConstraintsException failure) {
            GameplayDiagnosticCode code = failure.getMessage().contains("Depth")
                    ? GameplayDiagnosticCode.PREFAB_DEPTH_EXCEEDED
                    : GameplayDiagnosticCode.INVALID_PREFAB_VALUE;
            throw parseFailure(code, failure.getLocation(), "bounded JSON value",
                    failure.getOriginalMessage(), "Reduce the value to the documented limit.");
        } catch (JsonParseException failure) {
            GameplayDiagnosticCode code = failure.getOriginalMessage().contains("Duplicate field")
                    ? GameplayDiagnosticCode.DUPLICATE_JSON_KEY
                    : GameplayDiagnosticCode.MALFORMED_PREFAB_JSON;
            throw parseFailure(code, failure.getLocation(), "valid closed JSON",
                    failure.getOriginalMessage(),
                    code == GameplayDiagnosticCode.DUPLICATE_JSON_KEY
                            ? "Remove the duplicate object field."
                            : "Correct the JSON syntax and retry.");
        } catch (IOException failure) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.MALFORMED_PREFAB_JSON,
                    "parse-prefab-document",
                    "readable in-memory UTF-8 JSON",
                    bounded(failure.getMessage()),
                    "Correct the encoded JSON document and retry.");
        }
    }

    private PrefabCatalog parseRoot(JsonParser parser) throws IOException {
        String schemaVersion = null;
        List<LocatedPrefab> prefabs = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            requireToken(parser, JsonToken.FIELD_NAME, "root field name");
            String field = parser.currentName();
            JsonLocation fieldLocation = parser.currentLocation();
            if (!ROOT_FIELDS.contains(field)) {
                throw unknownField(field, "", fieldLocation, ROOT_FIELDS);
            }
            JsonToken valueToken = parser.nextToken();
            if ("schemaVersion".equals(field)) {
                schemaVersion = requireString(parser, valueToken, "/schemaVersion");
            } else {
                prefabs = parsePrefabs(parser, valueToken);
            }
        }
        if (schemaVersion == null) {
            throw missingField("", "schemaVersion");
        }
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw GameplayException.located(
                    GameplayDiagnosticCode.UNSUPPORTED_PREFAB_SCHEMA,
                    "parse-prefab-document",
                    Map.of("jsonPointer", "/schemaVersion"),
                    SCHEMA_VERSION,
                    schemaVersion,
                    "Set schemaVersion to the exact supported V1 identifier.");
        }
        if (prefabs == null) {
            throw missingField("", "prefabs");
        }
        Map<PrefabId, PrefabDefinition> definitions = new LinkedHashMap<>();
        for (LocatedPrefab located : prefabs) {
            PrefabDefinition prior = definitions.put(located.definition().id(),
                    located.definition());
            if (prior != null) {
                throw locatedFailure(GameplayDiagnosticCode.DUPLICATE_PREFAB_ID,
                        located.location(), located.pointer() + "/id",
                        "unique prefab ID", located.definition().id().value(),
                        "Remove or rename the duplicate prefab definition.");
            }
        }
        return new PrefabCatalog(definitions);
    }

    private List<LocatedPrefab> parsePrefabs(JsonParser parser, JsonToken token)
            throws IOException {
        if (token != JsonToken.START_ARRAY) {
            throw tokenFailure(parser, GameplayDiagnosticCode.INVALID_PREFAB_VALUE,
                    "prefabs array", token);
        }
        List<LocatedPrefab> result = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (result.size() >= limits.maxPrefabs()) {
                throw limitFailure(GameplayDiagnosticCode.PREFAB_COUNT_LIMIT_EXCEEDED,
                        parser.currentLocation(), "/prefabs",
                        limits.maxPrefabs(), result.size() + 1);
            }
            String pointer = "/prefabs/" + result.size();
            result.add(parsePrefab(parser, pointer));
        }
        return List.copyOf(result);
    }

    private LocatedPrefab parsePrefab(JsonParser parser, String pointer) throws IOException {
        requireToken(parser, JsonToken.START_OBJECT, "prefab object");
        JsonLocation location = parser.currentLocation();
        String id = null;
        Map<ComponentType<?>, Component> components = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            requireToken(parser, JsonToken.FIELD_NAME, "prefab field name");
            String field = parser.currentName();
            JsonLocation fieldLocation = parser.currentLocation();
            if (!PREFAB_FIELDS.contains(field)) {
                throw unknownField(field, pointer, fieldLocation, PREFAB_FIELDS);
            }
            JsonToken valueToken = parser.nextToken();
            if ("id".equals(field)) {
                id = requireString(parser, valueToken, pointer + "/id");
            } else {
                components = parseComponents(parser, valueToken, pointer + "/components");
            }
        }
        if (id == null) {
            throw missingField(pointer, "id");
        }
        if (components == null) {
            throw missingField(pointer, "components");
        }
        try {
            return new LocatedPrefab(new PrefabDefinition(PrefabId.of(id), components),
                    pointer, location);
        } catch (GameplayException failure) {
            throw GameplayException.located(
                    failure.code(),
                    failure.diagnostic().operation(),
                    sourceLocation(location, pointer + "/id"),
                    failure.diagnostic().expected(),
                    failure.diagnostic().observed(),
                    failure.diagnostic().correction());
        }
    }

    private Map<ComponentType<?>, Component> parseComponents(
            JsonParser parser, JsonToken token, String pointer) throws IOException {
        if (token != JsonToken.START_ARRAY) {
            throw tokenFailure(parser, GameplayDiagnosticCode.INVALID_PREFAB_VALUE,
                    "components array", token);
        }
        Map<ComponentType<?>, Component> components = new TreeMap<>();
        int index = 0;
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (index >= limits.maxComponentsPerPrefab()) {
                throw limitFailure(GameplayDiagnosticCode.PREFAB_COUNT_LIMIT_EXCEEDED,
                        parser.currentLocation(), pointer,
                        limits.maxComponentsPerPrefab(), index + 1);
            }
            String componentPointer = pointer + "/" + index;
            LocatedComponent located = parseComponent(parser, componentPointer);
            Component prior = components.put(located.type(), located.component());
            if (prior != null) {
                throw locatedFailure(GameplayDiagnosticCode.DUPLICATE_PREFAB_COMPONENT,
                        located.location(), componentPointer + "/type",
                        "one component per type", located.type().id(),
                        "Remove or merge the duplicate component declaration.");
            }
            index++;
        }
        return components;
    }

    private LocatedComponent parseComponent(JsonParser parser, String pointer) throws IOException {
        requireToken(parser, JsonToken.START_OBJECT, "component object");
        JsonLocation location = parser.currentLocation();
        Map<String, PrefabValue> values = new TreeMap<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            requireToken(parser, JsonToken.FIELD_NAME, "component field name");
            String field = parser.currentName();
            JsonToken valueToken = parser.nextToken();
            values.put(field, readValue(parser, valueToken, pointer + "/" + escape(field)));
        }
        PrefabValue typeValue = values.remove("type");
        if (typeValue == null) {
            throw missingField(pointer, "type");
        }
        if (!(typeValue instanceof PrefabValue.StringValue string)) {
            throw locatedFailure(GameplayDiagnosticCode.INVALID_PREFAB_VALUE,
                    typeValue, "string component type", describe(typeValue),
                    "Use the stable string ID of a registered component codec.");
        }
        ComponentCodec<?> codec;
        try {
            codec = codecs.require(string.value());
        } catch (GameplayException failure) {
            throw GameplayException.located(
                    failure.code(),
                    failure.diagnostic().operation(),
                    Map.of(
                            "jsonPointer", typeValue.pointer(),
                            "line", Long.toString(typeValue.line()),
                            "column", Long.toString(typeValue.column())),
                    failure.diagnostic().expected(),
                    failure.diagnostic().observed(),
                    failure.diagnostic().correction());
        }
        ComponentFields fields = new ComponentFields(values, pointer);
        try {
            Component component = codec.decode(fields);
            return new LocatedComponent(codec.type(), component, location);
        } catch (GameplayException failure) {
            if (!failure.diagnostic().location().isEmpty()) {
                throw failure;
            }
            throw GameplayException.located(
                    failure.code(),
                    failure.diagnostic().operation(),
                    sourceLocation(location, pointer),
                    failure.diagnostic().expected(),
                    failure.diagnostic().observed(),
                    failure.diagnostic().correction());
        } catch (RuntimeException failure) {
            throw locatedFailure(GameplayDiagnosticCode.INVALID_PREFAB_VALUE,
                    location, pointer, "valid " + codec.type().id() + " component",
                    bounded(failure.getMessage()),
                    "Use only documented enum names and values within component bounds.");
        }
    }

    private PrefabValue readValue(JsonParser parser, JsonToken token, String pointer)
            throws IOException {
        JsonLocation location = parser.currentLocation();
        return switch (token) {
            case VALUE_STRING -> new PrefabValue.StringValue(
                    parser.getText(), pointer, location.getLineNr(), location.getColumnNr());
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> numberValue(parser, pointer, location);
            case VALUE_TRUE, VALUE_FALSE -> new PrefabValue.BooleanValue(
                    token == JsonToken.VALUE_TRUE, pointer,
                    location.getLineNr(), location.getColumnNr());
            case VALUE_NULL -> new PrefabValue.NullValue(
                    pointer, location.getLineNr(), location.getColumnNr());
            case START_ARRAY -> readArray(parser, pointer, location);
            case START_OBJECT -> readObject(parser, pointer, location);
            default -> throw tokenFailure(parser, GameplayDiagnosticCode.INVALID_PREFAB_VALUE,
                    "JSON value", token);
        };
    }

    private PrefabValue numberValue(JsonParser parser, String pointer, JsonLocation location)
            throws IOException {
        String token = parser.getText();
        try {
            return new PrefabValue.NumberValue(token, new BigDecimal(token), pointer,
                    location.getLineNr(), location.getColumnNr());
        } catch (NumberFormatException failure) {
            throw locatedFailure(GameplayDiagnosticCode.INVALID_PREFAB_VALUE,
                    location, pointer, "finite decimal token", token,
                    "Use a JSON number representable as a finite component value.");
        }
    }

    private PrefabValue readArray(JsonParser parser, String pointer, JsonLocation location)
            throws IOException {
        List<PrefabValue> values = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (values.size() >= limits.maxArrayValues()) {
                throw limitFailure(GameplayDiagnosticCode.PREFAB_COUNT_LIMIT_EXCEEDED,
                        parser.currentLocation(), pointer,
                        limits.maxArrayValues(), values.size() + 1);
            }
            values.add(readValue(parser, parser.currentToken(), pointer + "/" + values.size()));
        }
        return new PrefabValue.ArrayValue(values, pointer,
                location.getLineNr(), location.getColumnNr());
    }

    private PrefabValue readObject(JsonParser parser, String pointer, JsonLocation location)
            throws IOException {
        Map<String, PrefabValue> values = new TreeMap<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            requireToken(parser, JsonToken.FIELD_NAME, "object field name");
            String field = parser.currentName();
            JsonToken valueToken = parser.nextToken();
            values.put(field, readValue(parser, valueToken, pointer + "/" + escape(field)));
        }
        return new PrefabValue.ObjectValue(values, pointer,
                location.getLineNr(), location.getColumnNr());
    }

    private static String requireString(JsonParser parser, JsonToken token, String pointer)
            throws IOException {
        if (token != JsonToken.VALUE_STRING) {
            throw tokenFailure(parser, GameplayDiagnosticCode.INVALID_PREFAB_VALUE,
                    "string at " + pointer, token);
        }
        return parser.getText();
    }

    private static void requireToken(JsonParser parser, JsonToken expected, String description) {
        if (parser.currentToken() != expected) {
            throw tokenFailure(parser, GameplayDiagnosticCode.MALFORMED_PREFAB_JSON,
                    description, parser.currentToken());
        }
    }

    private static GameplayException unknownField(
            String field, String parent, JsonLocation location, Set<String> accepted) {
        return locatedFailure(GameplayDiagnosticCode.UNKNOWN_PREFAB_FIELD,
                location, parent + "/" + escape(field), "one of " + accepted, field,
                "Remove the unknown field and use the closed V1 schema.");
    }

    private static GameplayException missingField(String parent, String field) {
        return GameplayException.located(
                GameplayDiagnosticCode.MISSING_PREFAB_FIELD,
                "parse-prefab-document",
                Map.of("jsonPointer", parent),
                "required field '" + field + "'",
                "missing",
                "Add the required field with its documented JSON type.");
    }

    private static GameplayException limitFailure(
            GameplayDiagnosticCode code,
            JsonLocation location,
            String pointer,
            int limit,
            int observed) {
        return locatedFailure(code, location, pointer,
                "at most " + limit + " entries", Integer.toString(observed),
                "Split or reduce the bounded collection.");
    }

    private static GameplayException tokenFailure(
            JsonParser parser,
            GameplayDiagnosticCode code,
            String expected,
            JsonToken observed) {
        return locatedFailure(code, parser.currentLocation(), "",
                expected, String.valueOf(observed), "Use the exact documented JSON shape.");
    }

    private static GameplayException parseFailure(
            GameplayDiagnosticCode code,
            JsonLocation location,
            String expected,
            String observed,
            String correction) {
        return locatedFailure(code, location, "", expected, bounded(observed), correction);
    }

    private static GameplayException locatedFailure(
            GameplayDiagnosticCode code,
            PrefabValue value,
            String expected,
            String observed,
            String correction) {
        return GameplayException.located(code, "parse-prefab-document",
                Map.of(
                        "jsonPointer", value.pointer(),
                        "line", Long.toString(value.line()),
                        "column", Long.toString(value.column())),
                expected, bounded(observed), correction);
    }

    private static GameplayException locatedFailure(
            GameplayDiagnosticCode code,
            JsonLocation location,
            String pointer,
            String expected,
            String observed,
            String correction) {
        return GameplayException.located(code, "parse-prefab-document",
                sourceLocation(location, pointer), expected, bounded(observed), correction);
    }

    private static Map<String, String> sourceLocation(JsonLocation location, String pointer) {
        if (location == null) {
            return Map.of("jsonPointer", pointer);
        }
        return Map.of(
                "jsonPointer", pointer,
                "line", Long.toString(location.getLineNr()),
                "column", Long.toString(location.getColumnNr()));
    }

    private static String describe(PrefabValue value) {
        return value.getClass().getSimpleName();
    }

    private static String escape(String field) {
        return field.replace("~", "~0").replace("/", "~1");
    }

    private static String bounded(String value) {
        if (value == null) {
            return "unavailable";
        }
        return value.length() <= 512 ? value : value.substring(0, 512);
    }

    private record LocatedPrefab(
            PrefabDefinition definition,
            String pointer,
            JsonLocation location) {
    }

    private record LocatedComponent(
            ComponentType<?> type,
            Component component,
            JsonLocation location) {
    }
}
