package com.cedarsoftware.io;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.cedarsoftware.io.factory.ArrayFactory;
import com.cedarsoftware.io.factory.ConvertableFactory;
import com.cedarsoftware.io.factory.EnumClassFactory;
import com.cedarsoftware.io.factory.ThrowableFactory;
import com.cedarsoftware.io.reflect.Injector;
import com.cedarsoftware.io.reflect.InjectorFactory;
import com.cedarsoftware.io.reflect.factories.MethodInjectorFactory;
import com.cedarsoftware.io.reflect.filters.FieldFilter;
import com.cedarsoftware.io.reflect.filters.field.EnumFieldFilter;
import com.cedarsoftware.io.reflect.filters.field.StaticFieldFilter;
import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.Convention;
import com.cedarsoftware.util.convert.CommonValues;
import com.cedarsoftware.util.convert.Convert;
import com.cedarsoftware.util.convert.ConverterOptions;

import static com.cedarsoftware.io.MetaUtils.loadMapDefinition;

/**
 * Builder class for building the writeOptions.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 * @author Kenny Partlow (kpartlow@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.*
 */
public class ReadOptionsBuilder {

    private static final Map<Class<?>, JsonReader.JsonClassReader> BASE_READERS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, JsonReader.ClassFactory> BASE_CLASS_FACTORIES = new ConcurrentHashMap<>();
    private static final Map<String, String> BASE_ALIAS_MAPPINGS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Class<?>> BASE_COERCED_TYPES = new ConcurrentHashMap<>();
    private static final Set<Class<?>> BASE_NON_REFS = ConcurrentHashMap.newKeySet();
    private static final Map<Class<?>, Map<String, String>> BASE_NONSTANDARD_MAPPINGS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Set<String>> BASE_EXCLUDED_FIELD_NAMES = new ConcurrentHashMap<>();

    static {
        // ClassFactories
        BASE_CLASS_FACTORIES.putAll(loadClassFactory());
        BASE_READERS.putAll(loadReaders());
        BASE_ALIAS_MAPPINGS.putAll(loadMapDefinition("config/aliases.txt"));
        BASE_COERCED_TYPES.putAll(loadCoercedTypes());      // Load coerced types from resource/coerced.txt
        BASE_NON_REFS.addAll(MetaUtils.loadNonRefs());
        BASE_EXCLUDED_FIELD_NAMES.putAll(MetaUtils.loadClassToSetOfStrings("config/excludedInjectorFields.txt"));
        BASE_NONSTANDARD_MAPPINGS.putAll(MetaUtils.loadNonStandardMethodNames("config/nonStandardInjectors.txt"));
    }

    private final DefaultReadOptions options;

    /**
     * Start with default options
     */
    public ReadOptionsBuilder() {
        options = new DefaultReadOptions();

        // Direct copy (with swap) without classForName() lookups for speed.
        // (The classForName check is done with the BASE_ALIAS_MAPPINGS are loaded)
        BASE_ALIAS_MAPPINGS.forEach((srcType, alias) -> {
            options.aliasTypeNames.put(alias, srcType);
        });

        options.injectorFactories.add(new MethodInjectorFactory());

        options.fieldFilters.add(new StaticFieldFilter());
        options.fieldFilters.add(new EnumFieldFilter());
        
        options.coercedTypes.putAll(BASE_COERCED_TYPES);
        options.customReaderClasses.putAll(BASE_READERS);
        options.classFactoryMap.putAll(BASE_CLASS_FACTORIES);
        options.nonRefClasses.addAll(BASE_NON_REFS);
        options.nonStandardMappings.putAll(BASE_NONSTANDARD_MAPPINGS);
        options.excludedFieldNames.putAll(WriteOptionsBuilder.BASE_EXCLUDED_FIELD_NAMES);
        options.excludedInjectorFields.putAll(BASE_EXCLUDED_FIELD_NAMES);
    }

    /**
     * Start with a copy of another ReadOptions
     */
    public ReadOptionsBuilder(ReadOptions copy) {
        this(); // initialize to empty

        ReadOptionsBuilder.DefaultReadOptions other = (ReadOptionsBuilder.DefaultReadOptions) copy;
        options.aliasTypeNames.clear();
        options.aliasTypeNames.putAll(other.aliasTypeNames);

        options.injectorFactories.clear();
        options.injectorFactories.addAll(other.injectorFactories);

        options.fieldFilters.clear();
        options.fieldFilters.addAll(other.fieldFilters);

        options.excludedFieldNames.clear();
        options.excludedFieldNames.putAll(other.excludedFieldNames);

        options.excludedInjectorFields.clear();
        options.excludedInjectorFields.putAll(other.excludedInjectorFields);

        options.nonStandardMappings.clear();
        options.nonStandardMappings.putAll(other.nonStandardMappings);

        options.coercedTypes.clear();
        options.coercedTypes.putAll(other.coercedTypes);

        options.customReaderClasses.clear();
        options.customReaderClasses.putAll(other.customReaderClasses);

        options.classFactoryMap.clear();
        options.classFactoryMap.putAll(other.classFactoryMap);

        options.nonRefClasses.clear();
        options.nonRefClasses.addAll(other.nonRefClasses);
    }

    /**
     * Call this method to add a permanent (JVM lifetime) coercion of one
     * class to another during instance creation.  Examples of classes
     * that might need this are proxied classes such as HibernateBags, etc.
     * That you want to create as regular jvm collections.
     *
     * @param sourceClass String class name (fully qualified name) that will be coerced to another type.
     * @param factory     JsonReader.ClassFactory instance which can create the sourceClass and load it's contents,
     *                    using passed in JsonValues (JsonObject or JsonArray).
     */
    public static void addPermanentClassFactory(Class<?> sourceClass, JsonReader.ClassFactory factory) {
        BASE_CLASS_FACTORIES.put(sourceClass, factory);
    }

    /**
     * Call this method to add a permanent (JVM lifetime) coercion of one
     * class to another during instance creation.  Examples of classes
     * that might need this are proxied classes such as HibernateBags, etc.
     * That you want to create as regular jvm collections.
     *
     * @param sourceClass      String class name (fully qualified name) that will be coerced to another type.
     * @param destinationClass Class to coerce to.  For example, java.util.Collections$EmptyList to java.util.ArrayList.
     */
    public static void addPermanentCoercedType(Class<?> sourceClass, Class<?> destinationClass) {
        BASE_COERCED_TYPES.put(sourceClass, destinationClass);
    }

    /**
     * Call this method to add a permanent (JVM lifetime) alias of a class to an often shorter, name.
     *
     * @param sourceClass String class name (fully qualified name) that will be aliased by a shorter name in the JSON.
     * @param alias       Shorter alias name, for example, "ArrayList" as opposed to "java.util.ArrayList"
     */
    public static void addPermanentAlias(Class<?> sourceClass, String alias) {
        BASE_ALIAS_MAPPINGS.put(sourceClass.getName(), alias);
    }

    /**
     * Call this method to add a custom JSON reader to json-io.  It will
     * associate the Class 'c' to the reader you pass in.  The readers are
     * found with isAssignableFrom().  If this is too broad, causing too
     * many classes to be associated to the custom reader, you can indicate
     * that json-io should not use a custom reader for a particular class,
     * by calling the addNotCustomReader() method.  This method will add
     * the custom reader such that it will be there permanently, for the
     * life of the JVM (static).
     *
     * @param c      Class to assign a custom JSON reader to
     * @param reader The JsonClassReader which will read the custom JSON format of 'c'
     */
    public static void addPermanentReader(Class<?> c, JsonReader.JsonClassReader reader) {
        BASE_READERS.put(c, reader);
    }

    /**
     * @param classToCreate Class that will need to use a JsonReader.ClassFactory for instantiation and loading.
     * @param factory       JsonReader.ClassFactory instance that has been created to instantiate a difficult
     *                      to create and load class (class c).
     */
    public static void assignInstantiator(Class<?> classToCreate, JsonReader.ClassFactory factory) {
        BASE_CLASS_FACTORIES.put(classToCreate, factory);
    }

    /**
     * @return ReadOptions - built options
     */
    public ReadOptions build() {
        options.clearCaches();
        options.aliasTypeNames = Collections.unmodifiableMap(options.aliasTypeNames);
        options.coercedTypes = Collections.unmodifiableMap(options.coercedTypes);
        options.notCustomReadClasses = Collections.unmodifiableSet(options.notCustomReadClasses);
        options.customReaderClasses = Collections.unmodifiableMap(options.customReaderClasses);
        options.classFactoryMap = Collections.unmodifiableMap(options.classFactoryMap);
        options.nonRefClasses = Collections.unmodifiableSet(options.nonRefClasses);
        options.converterOptions.converterOverrides = Collections.unmodifiableMap(options.converterOptions.converterOverrides);
        options.converterOptions.customOptions = Collections.unmodifiableMap(options.converterOptions.customOptions);
        options.excludedFieldNames = Collections.unmodifiableMap(options.excludedFieldNames);
        options.excludedInjectorFields = Collections.unmodifiableMap(options.excludedInjectorFields);
        options.fieldFilters = Collections.unmodifiableList(options.fieldFilters);
        options.injectorFactories = Collections.unmodifiableList(options.injectorFactories);
        options.nonStandardMappings = Collections.unmodifiableMap(options.nonStandardMappings);

        return options;
    }

    /**
     * Add a class to the not-customized list - the list of classes that you do not want to be picked up by a
     * custom reader (that could happen through inheritance).
     *
     * @param notCustomClass Class to add to the not-customized list.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder addNotCustomReaderClass(Class<?> notCustomClass) {
        options.notCustomReadClasses.add(notCustomClass);
        return this;
    }

    /**
     * @param notCustomClasses initialize the list of classes on the non-customized list.  All prior associations
     *                         will be dropped and this Collection will establish the new list.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder replaceNotCustomReaderClasses(Collection<Class<?>> notCustomClasses) {
        options.notCustomReadClasses.clear();
        options.notCustomReadClasses.addAll(notCustomClasses);
        return this;
    }

    /**
     * @param source             source class.
     * @param target             target class.
     * @param conversionFunction functional interface to run Conversion.
     * @return ReadOptionsBuilder for chained access.
     */

    public ReadOptionsBuilder addConverterOverride(Class<?> source, Class<?> target, Convert<?> conversionFunction) {
        source = ClassUtilities.toPrimitiveWrapperClass(source);
        target = ClassUtilities.toPrimitiveWrapperClass(target);
        options.converterOptions.converterOverrides.put(new AbstractMap.SimpleImmutableEntry<>(source, target), conversionFunction);
        return this;
    }

    /**
     * @param customReaderClasses Map of Class to JsonReader.JsonClassReader.  Establish the passed in Map as the
     *                            established Map of custom readers to be used when reading JSON. Using this method
     *                            more than once, will set the custom readers to only the values from the Set in
     *                            the last call made.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder replaceCustomReaderClasses(Map<? extends Class<?>, ? extends JsonReader.JsonClassReader> customReaderClasses) {
        options.customReaderClasses.clear();
        options.customReaderClasses.putAll(customReaderClasses);
        return this;
    }

    /**
     * @param clazz        Class to add a custom reader for.
     * @param customReader JsonClassReader to use when the passed in Class is encountered during serialization.
     *                     Custom readers are passed an empty instance.  Use a ClassFactory if you want to instantiate
     *                     and load the class in one step.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder addCustomReaderClass(Class<?> clazz, JsonReader.JsonClassReader customReader) {
        options.customReaderClasses.put(clazz, customReader);
        return this;
    }

    /**
     * Associate multiple ClassFactory instances to Classes that needs help being constructed and read in.
     *
     * @param factories Map of entries that are Class to Factory. The Factory class knows how to instantiate
     *                  and load the class associated to it.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder replaceClassFactories(Map<Class<?>, ? extends JsonReader.ClassFactory> factories) {
        options.classFactoryMap.clear();
        options.classFactoryMap.putAll(factories);
        return this;
    }

    /**
     * Associate a ClassFactory to a Class that needs help being constructed and read in.
     *
     * @param clazz   Class that is difficult to instantiate.
     * @param factory Class written to instantiate the 'clazz' and load it's values.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder addClassFactory(Class<?> clazz, JsonReader.ClassFactory factory) {
        options.classFactoryMap.put(clazz, factory);
        return this;
    }

    /**
     * Set to return as JSON_OBJECTS's (faster, useful for large, more simple object data sets). This returns as
     * native json types (Map, Object[], long, double, boolean)
     */
    public ReadOptionsBuilder returnAsNativeJsonObjects() {
        options.returnType = ReadOptions.ReturnType.JSON_OBJECTS;
        return this;
    }

    /**
     * Return as JAVA_OBJECT's the returned value will be of the class type passed into JsonReader.toJava(json, rootClass).
     * This mode is good for cloning exact objects.
     */
    public ReadOptionsBuilder returnAsJavaObjects() {
        options.returnType = ReadOptions.ReturnType.JAVA_OBJECTS;
        return this;
    }

    /**
     * @param classLoader ClassLoader to use when reading JSON to resolve String named classes.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder classLoader(ClassLoader classLoader) {
        Convention.throwIfNull(classLoader, "classloader cannot be null");
        options.converterOptions.classloader = classLoader;
        return this;
    }

    /**
     * @param fail boolean indicating whether we should fail on unknown type encountered.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder failOnUnknownType(boolean fail) {
        options.failOnUnknownType = fail;
        return this;
    }

    /**
     * Set a class to use when the JSON reader cannot instantiate a class. When that happens, it will throw
     * a JsonIoException() if no 'unknownTypeClass' is set, otherwise it will create the instance of the class
     * specified as the 'unknownTypeClass.' A common one to use, for example, if java.util.LinkedHashMap.
     *
     * @param c Class to instantiate when a String specified class in the JSON cannot be instantiated.  Set to null
     *          if you want a JsonIoException to be thrown when this happens.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder unknownTypeClass(Class<?> c) {
        options.unknownTypeClass = c;
        return this;
    }

    /**
     * @param closeStream boolean set to 'true' to have JsonIo close the InputStream when it is finished reading
     *                    from it.  The default is 'true'.  If false, the InputStream will not be closed, allowing
     *                    you to continue reading further.  Example, NDJSON that has new line eliminated JSON
     *                    objects repeated.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder closeStream(boolean closeStream) {
        options.closeStream = closeStream;
        return this;
    }

    /**
     * @param maxDepth int maximum of the depth that JSON Objects {...} can be nested.  Set this to prevent
     *                 security risk from StackOverflow attack vectors.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder maxDepth(int maxDepth) {
        options.maxDepth = maxDepth;
        return this;
    }

    /**
     * @param allowNanAndInfinity boolean 'allowNanAndInfinity' setting.  true will allow Double and Floats to be
     *                            read in as NaN and +Inf, -Inf [infinity], false and a JsonIoException will be
     *                            thrown if these values are encountered
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder allowNanAndInfinity(boolean allowNanAndInfinity) {
        options.allowNanAndInfinity = allowNanAndInfinity;
        return this;
    }

    /**
     * @param aliasTypeNames Map containing String class names to alias names.  The passed in Map will
     *                       be copied, and be the new baseline settings.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder aliasTypeNames(Map<String, String> aliasTypeNames) {
        aliasTypeNames.forEach(this::addUniqueAlias);
        return this;
    }

    /**
     * @param type  String class name
     * @param alias String shorter name to use, typically.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder aliasTypeName(Class<?> type, String alias) {
        addUniqueAlias(type.getName(), alias);
        return this;
    }

    /**
     * @param typeName String class name
     * @param alias    String shorter name to use, typically.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder aliasTypeName(String typeName, String alias) {
        addUniqueAlias(typeName, alias);
        return this;
    }

    /**
     * @return boolean true if set to always show type (@type)
     */
    public ReadOptionsBuilder withExtendedAliases() {
        Map<String, String> extendedAliases = MetaUtils.loadMapDefinition("config/extendedAliases.txt");
        extendedAliases.forEach((key, value) -> options.aliasTypeNames.putIfAbsent(value, key));
        return this;
    }

    /**
     * @param locale target locale for conversions
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder setLocale(Locale locale) {
        options.converterOptions.locale = locale;
        return this;
    }

    /**
     * @param charset source charset for conversions
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder setCharset(Charset charset) {
        options.converterOptions.charset = charset;
        return this;
    }

    /**
     * @param zoneId source charset for conversions
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder setZoneId(ZoneId zoneId) {
        options.converterOptions.zoneId = zoneId;
        return this;
    }

    /**
     * @param ch Character used when converting true -> char
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder setTrueCharacter(Character ch) {
        options.converterOptions.trueChar = ch;
        return this;
    }

    /**
     * @param ch Character used when converting false -> char
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder setFalseCharacter(Character ch) {
        options.converterOptions.falseChar = ch;
        return this;
    }

    /**
     * Add a custom option, which may be useful when writing custom readers.
     * @param key String name of the custom option
     * @param value Object value of the custom option
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder addCustomOption(String key, Object value) {
        if (key == null) {
            throw new JsonIoException("Custom option key must not be null.");
        }
        if (value == null) {
            options.customOptions.remove(key);
        } else {
            options.customOptions.put(key, value);
        }
        return this;
    }

    /**
     * Since we are swapping keys/values, we must check for duplicate values (which are now keys).
     *
     * @param type  String fully qualified class name.
     * @param alias String shorter alias name.
     */
    private void addUniqueAlias(String type, String alias) {
        Class<?> clazz = ClassUtilities.forName(type, options.getClassLoader());
        if (clazz == null) {
            System.out.println("Unknown class: " + type + " cannot be added to the ReadOptions alias map.");
        }
        String existType = options.aliasTypeNames.get(alias);
        if (existType != null) {
            System.out.println("Non-unique ReadOptions alias: " + alias + " attempted assign to: " + type + ", but is already assigned to: " + existType);
        }
        options.aliasTypeNames.put(alias, type);
    }

    /**
     * Coerce a class from one type (named in the JSON) to another type.  For example, converting
     * "java.util.Collections$SingletonSet" to a LinkedHashSet.class.
     *
     * @param sourceClassName String class name to coerce to another type.
     * @param destClass       Class to coerce into.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder coerceClass(String sourceClassName, Class<?> destClass) {
        Class<?> clazz = ClassUtilities.forName(sourceClassName, options.getClassLoader());
        if (clazz != null) {
            options.coercedTypes.put(clazz, destClass);
        }
        return this;
    }

    /**
     * @param missingFieldHandler JsonReader.MissingFieldHandler implementation to call.  This method will be
     *                            called when a field in the JSON is read in, yet there is no corresponding field on the destination object to
     *                            receive the value.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder missingFieldHandler(JsonReader.MissingFieldHandler missingFieldHandler) {
        options.missingFieldHandler = missingFieldHandler;
        return this;
    }

    /**
     * @param clazz class to add to be considered a non-referenceable object.  Just like an "int" for example, any
     *              class added here will never use an @id/@ref pair.  The downside, is that when read,
     *              each instance, even if the same as another, will use memory.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder addNonReferenceableClass(Class<?> clazz) {
        options.nonRefClasses.add(clazz);
        return this;
    }

    /**
     * Load JsonReader.ClassFactory classes based on contents of resources/classFactory.txt.
     * Verify that classes listed are indeed valid classes loaded in the JVM.
     *
     * @return Map<Class < ?>, JsonReader.ClassFactory> containing the resolved Class -> JsonClassFactory instance.
     */
    private static Map<Class<?>, JsonReader.ClassFactory> loadClassFactory() {
        Map<String, String> map = MetaUtils.loadMapDefinition("config/classFactory.txt");
        Map<Class<?>, JsonReader.ClassFactory> factories = new HashMap<>();
        ClassLoader classLoader = ReadOptions.class.getClassLoader();

        for (Map.Entry<String, String> entry : map.entrySet()) {
            String className = entry.getKey();
            String factoryClassName = entry.getValue();
            Class<?> clazz = ClassUtilities.forName(className, classLoader);

            if (clazz == null) {
                System.out.println("Skipping class: " + className + " not defined in JVM, but listed in resources/classFactories.txt");
                continue;
            }

            if (factoryClassName.equalsIgnoreCase("Convertable")) {
                factories.put(clazz, new ConvertableFactory<>(clazz));
            } else if (factoryClassName.equalsIgnoreCase("ArrayFactory")) {
                factories.put(clazz, new ArrayFactory<>(clazz));
            } else {
                try {
                    Class<? extends JsonReader.ClassFactory> factoryClass = (Class<? extends JsonReader.ClassFactory>) ClassUtilities.forName(factoryClassName, classLoader);
                    if (factoryClass == null) {
                        System.out.println("Skipping class: " + factoryClassName + " not defined in JVM, but listed in resources/classFactories.txt, as factory for: " + className);
                        continue;
                    }
                    factories.put(clazz, factoryClass.getConstructor().newInstance());
                } catch (Exception e) {
                    System.out.println("Unable to create JsonReader.ClassFactory class: " + factoryClassName + ", a factory class for: " + className + ", listed in resources/classFactories.txt");
                }
            }
        }
        return factories;
    }

    /**
     * Load custom reader classes based on contents of resources/customReaders.txt.
     * Verify that classes listed are indeed valid classes loaded in the JVM.
     *
     * @return Map<Class < ?>, JsonReader.JsonClassReader> containing the resolved Class -> JsonClassReader instance.
     */
    private static Map<Class<?>, JsonReader.JsonClassReader> loadReaders() {
        Map<String, String> map = MetaUtils.loadMapDefinition("config/customReaders.txt");
        Map<Class<?>, JsonReader.JsonClassReader> readers = new HashMap<>();
        ClassLoader classLoader = ReadOptions.class.getClassLoader();

        for (Map.Entry<String, String> entry : map.entrySet()) {
            String className = entry.getKey();
            String readerClassName = entry.getValue();
            Class<?> clazz = ClassUtilities.forName(className, classLoader);
            if (clazz != null) {
                Class<JsonReader.JsonClassReader> customReaderClass = (Class<JsonReader.JsonClassReader>) ClassUtilities.forName(readerClassName, classLoader);

                try {
                    readers.put(clazz, customReaderClass.getConstructor().newInstance());
                } catch (Exception e) {
                    System.out.println("Note: could not instantiate (custom JsonClassReader class): " + readerClassName + " from resources/customReaders.txt");
                }
            } else {
                System.out.println("Class: " + className + " not defined in JVM, but listed in resources/customReaders.txt");
            }
        }
        return readers;
    }

    /**
     * Load custom writer classes based on contents of customWriters.txt in the resources folder.
     * Verify that classes listed are indeed valid classes loaded in the JVM.
     *
     * @return Map<Class < ?>, JsonWriter.JsonClassWriter> containing the resolved Class -> JsonClassWriter instance.
     */
    private static Map<Class<?>, Class<?>> loadCoercedTypes() {
        Map<String, String> map = MetaUtils.loadMapDefinition("config/coercedTypes.txt");
        Map<Class<?>, Class<?>> coerced = new HashMap<>();
        ClassLoader classLoader = ReadOptions.class.getClassLoader();

        for (Map.Entry<String, String> entry : map.entrySet()) {
            String srcClassName = entry.getKey();
            String destClassName = entry.getValue();
            Class<?> srcType = ClassUtilities.forName(srcClassName, classLoader);
            if (srcType == null) {
                System.out.println("Skipping class coercion for source class: " + srcClassName + " (not found) to: " + destClassName + ", listed in resources/coercedTypes.txt");
                continue;
            }
            Class<?> destType = ClassUtilities.forName(destClassName, classLoader);
            if (destType == null) {
                System.out.println("Skipping class coercion for source class: " + srcClassName + " cannot be mapped to: " + destClassName + " (not found), listed in resources/coercedTypes.txt");
                continue;
            }
            coerced.put(srcType, destType);
        }
        return coerced;
    }

    public static class DefaultConverterOptions implements ConverterOptions {
        private ZoneId zoneId = ZoneId.systemDefault();
        private Locale locale = Locale.getDefault();
        private Charset charset = StandardCharsets.UTF_8;
        private ClassLoader classloader = DefaultConverterOptions.class.getClassLoader();
        private Character trueChar = CommonValues.CHARACTER_ONE;
        private Character falseChar = CommonValues.CHARACTER_ZERO;
        private Map<String, Object> customOptions = new ConcurrentHashMap<>();
        private Map<Map.Entry<Class<?>, Class<?>>, Convert<?>> converterOverrides = new ConcurrentHashMap<>(100, .8f);

        public ZoneId getZoneId() {
            return zoneId;
        }

        public Locale getLocale() {
            return locale;
        }

        public Charset getCharset() {
            return charset;
        }

        public ClassLoader getClassLoader() {
            return classloader;
        }

        public Character trueChar() {
            return trueChar;
        }

        public Character falseChar() {
            return falseChar;
        }

        public Map<Map.Entry<Class<?>, Class<?>>, Convert<?>> getConverterOverrides() {
            return converterOverrides;
        }

        public <T> T getCustomOption(String name) {
            return (T) customOptions.get(name);
        }
    }

    static class DefaultReadOptions implements ReadOptions {
        private Class<?> unknownTypeClass = null;
        private boolean failOnUnknownType = false;
        private boolean closeStream = true;
        private int maxDepth = 1000;
        private JsonReader.MissingFieldHandler missingFieldHandler = null;
        private final DefaultConverterOptions converterOptions = new DefaultConverterOptions();
        private final Map<String, Object> customOptions = new HashMap<>();
        private ReadOptions.ReturnType returnType = ReadOptions.ReturnType.JAVA_OBJECTS;
        private boolean allowNanAndInfinity = false;
        private Map<String, String> aliasTypeNames = new HashMap<>();
        private Map<Class<?>, Class<?>> coercedTypes = new HashMap<>();
        private Set<Class<?>> notCustomReadClasses = new HashSet<>();
        private Map<Class<?>, JsonReader.JsonClassReader> customReaderClasses = new HashMap<>();
        private Map<Class<?>, JsonReader.ClassFactory> classFactoryMap = new HashMap<>();
        private Set<Class<?>> nonRefClasses = new HashSet<>();
        private Map<Class<?>, Set<String>> excludedFieldNames = new HashMap<>();
        private Map<Class<?>, Set<String>> excludedInjectorFields = new HashMap<>();
        private List<FieldFilter> fieldFilters = new ArrayList<>();
        private List<InjectorFactory> injectorFactories = new ArrayList<>();

        // Creating the Accessors (methodHandles) is expensive so cache the list of Accessors per Class
        private final Map<Class<?>, Map<String, Injector>> injectorsCache = new ConcurrentHashMap<>(200, 0.8f, Runtime.getRuntime().availableProcessors());
        private Map<Class<?>, Map<String, String>> nonStandardMappings = new HashMap<>();

        // Runtime cache (not feature options)
        private final Map<Class<?>, JsonReader.JsonClassReader> readerCache = new ConcurrentHashMap<>(300);
        private final JsonReader.ClassFactory throwableFactory = new ThrowableFactory();
        private final JsonReader.ClassFactory enumFactory = new EnumClassFactory();

        //  Cache of fields used for accessors.  controlled by ignoredFields
        private final Map<Class<?>, Map<String, Field>> classMetaCache = new ConcurrentHashMap<>(200, 0.8f, Runtime.getRuntime().availableProcessors());
        
        /**
         * Default constructor.  Prevent instantiation outside of package.
         */
        private DefaultReadOptions() {
        }

        /**
         * @return boolean will return true if NAN and Infinity are allowed to be read in as Doubles and Floats,
         * else a JsonIoException will be thrown if these are encountered.  default is false per JSON standard.
         */
        public boolean isAllowNanAndInfinity() {
            return allowNanAndInfinity;
        }

        /**
         * @return ClassLoader to be used when reading JSON to resolve String named classes.
         */
        public ClassLoader getClassLoader() {
            return this.converterOptions.getClassLoader();
        }

        /**
         * @return boolean true if an 'unknownTypeClass' is set, false if it is not sell (null).
         */
        public boolean isFailOnUnknownType() {
            return failOnUnknownType;
        }

        /**
         * @return the Class which will have unknown fields set upon it.  Typically this is a Map derivative.
         */
        public Class<?> getUnknownTypeClass() {
            return unknownTypeClass;
        }

        /**
         * @return boolean 'true' if the InputStream should be closed when the reading is finished.  The default is 'true.'
         */
        public boolean isCloseStream() {
            return closeStream;
        }

        /**
         * @return int maximum level the JSON can be nested.  Once the parsing nesting level reaches this depth, a
         * JsonIoException will be thrown instead of a StackOverflowException.  Prevents security risk from StackOverflow
         * attack vectors.
         */
        public int getMaxDepth() {
            return maxDepth;
        }
        
        /**
         * Alias Type Names, e.g. "ArrayList" instead of "java.util.ArrayList".
         *
         * @param typeName String name of type to fetch alias for.  There are no default aliases.
         * @return String alias name or null if type name is not aliased.
         */
        public String getTypeNameAlias(String typeName) {
            String alias = aliasTypeNames.get(typeName);
            return alias == null ? typeName : alias;
        }

        /**
         * @return boolean true if the passed in Class name is being coerced to another type, false otherwise.
         */
        public boolean isClassCoerced(String className) {
            return coercedTypes.containsKey(className);
        }

        /**
         * Fetch the coerced class for the passed in fully qualified class name.
         *
         * @param c Class to coerce
         * @return Class destination (coerced) class or null if there is none.
         */
        public Class<?> getCoercedClass(Class<?> c) {
            return coercedTypes.get(c);
        }

        /**
         * @return JsonReader.MissingFieldHandler to be called when a field in the JSON is read in, yet there is no
         * corresponding field on the destination object to receive the field value.
         */
        public JsonReader.MissingFieldHandler getMissingFieldHandler() {
            return missingFieldHandler;
        }

        /**
         * @param clazz Class to check to see if it is non-referenceable.  Non-referenceable classes will always create
         *              a new instance when read in and never use @id/@ref. This uses more memory when the JSON is read in,
         *              as there will be a separate instance in memory for each occurrence. There are certain classes that
         *              json-io automatically treats as non-referenceable, like Strings, Enums, Class, and any Number
         *              instance (BigDecimal, AtomicLong, etc.)  You can add to this list. Often, non-referenceable classes
         *              are useful for classes that can be defined in one line as a JSON, like a ZonedDateTime, for example.
         * @return boolean true if the passed in class is considered a non-referenceable class.
         */
        public boolean isNonReferenceableClass(Class<?> clazz) {
            return nonRefClasses.contains(clazz) ||     // Covers primitives, primitive wrappers, Atomic*, Big*, String
                    Number.class.isAssignableFrom(clazz) ||
                    Date.class.isAssignableFrom(clazz) ||
                    String.class.isAssignableFrom(clazz) ||
                    clazz.isEnum();
        }

        /**
         * @param clazz Class to see if it is on the not-customized list.  Classes are added to this list when
         *              a class is being picked up through inheritance, and you don't want it to have a custom
         *              reader associated to it.
         * @return boolean true if the passed in class is on the not-customized list, false otherwise.
         */
        public boolean isNotCustomReaderClass(Class<?> clazz) {
            return notCustomReadClasses.contains(clazz);
        }

        /**
         * @param clazz Class to check to see if there is a custom reader associated to it.
         * @return boolean true if there is an associated custom reader class associated to the passed in class,
         * false otherwise.
         */
        public boolean isCustomReaderClass(Class<?> clazz) {
            return customReaderClasses.containsKey(clazz);
        }

        /**
         * Get the ClassFactory associated to the passed in class.
         *
         * @param c Class for which to fetch the ClassFactory.
         * @return JsonReader.ClassFactory instance associated to the passed in class.
         */
        public JsonReader.ClassFactory getClassFactory(Class<?> c) {
            if (c == null) {
                return null;
            }

            JsonReader.ClassFactory factory = this.classFactoryMap.get(c);

            if (factory != null) {
                return factory;
            }

            if (Throwable.class.isAssignableFrom(c)) {
                return throwableFactory;
            }

            Optional optional = MetaUtils.getClassIfEnum(c);

            if (optional.isPresent()) {
                return enumFactory;
            }

            return null;
        }

        /**
         * Dummy place-holder class exists only because ConcurrentHashMap cannot contain a
         * null value.  Instead, singleton instance of this class is placed where null values
         * are needed.
         */
        private static final class NullClass implements JsonReader.JsonClassReader {
        }

        private static final NullClass nullReader = new NullClass();

        /**
         * Fetch the custom reader for the passed in Class.  If it is cached (already associated to the
         * passed in Class), return the same instance, otherwise, make a call to get the custom reader
         * and store that result.
         *
         * @param c Class of object for which fetch a custom reader
         * @return JsonClassReader for the custom class (if one exists), null otherwise.
         */
        public JsonReader.JsonClassReader getCustomReader(Class<?> c) {
            JsonReader.JsonClassReader reader = readerCache.computeIfAbsent(c, cls -> MetaUtils.findClosest(c, customReaderClasses, nullReader));
            return reader == nullReader ? null : reader;
        }

        /**
         * @return true if returning items in basic JSON object format
         */
        public boolean isReturningJsonObjects() {
            return returnType == ReadOptions.ReturnType.JSON_OBJECTS;
        }

        /**
         * @return true if returning items in full Java object formats.  Useful for accurate reproduction of graphs
         * into the orginal types such as when cloning objects.
         */
        public boolean isReturningJavaObjects() {
            return returnType == ReadOptions.ReturnType.JAVA_OBJECTS;
        }

        public Map<String, Injector> getDeepInjectorMap(Class<?> classToTraverse) {
            if (classToTraverse == null) {
                return Collections.emptyMap();
            }
            return this.injectorsCache.computeIfAbsent(classToTraverse, this::buildInjectors);
        }

        public void clearCaches() {
            injectorsCache.clear();
        }

        private Map<String, Injector> buildInjectors(Class<?> c) {
            final Map<String, Field> fields = getDeepDeclaredFields(c);
            final Map<String, Injector> injectors = new LinkedHashMap<>(fields.size());

            for (final Map.Entry<String, Field> entry : fields.entrySet()) {
                final Field field = entry.getValue();

                final String fieldName = entry.getKey();
                Injector injector = this.findInjector(field, fieldName);

                if (injector == null) {
                    injector = Injector.create(field, fieldName);
                }

                if (injector != null) {
                    injectors.put(fieldName, injector);
                }

            }
            return injectors;
        }

        private Injector findInjector(Field field, String key) {
            for (final InjectorFactory factory : this.injectorFactories) {
                try {
                    final Injector injector = factory.createInjector(field, this.nonStandardMappings, key);

                    if (injector != null) {
                        return injector;
                    }
                } catch (Exception ignore) {
                }
            }
            return null;
        }

        /**
         * Gets the declared fields for the full class hierarchy of a given class
         *
         * @param c - given class.
         * @return Map - map of string fieldName to Field Object.  This will have the
         * deep list of fields for a given class.
         */
        public Map<String, Field> getDeepDeclaredFields(final Class<?> c) {
            return classMetaCache.computeIfAbsent(c, this::buildDeepFieldMap);
        }

        public ConverterOptions getConverterOptions() {
            return this.converterOptions;
        }

        public Object getCustomOption(String key)
        {
            return customOptions.get(key);
        }

        /**
         * Gets the declared fields for the full class hierarchy of a given class
         *
         * @param c - given class.
         * @return Map - map of string fieldName to Field Object.  This will have the
         * deep list of fields for a given class.
         */
        public Map<String, Field> buildDeepFieldMap(final Class<?> c) {
            Convention.throwIfNull(c, "class cannot be null");

            final Map<String, Field> map = new LinkedHashMap<>();
            final Set<String> exclusions = new HashSet<>();

            Class<?> curr = c;
            while (curr != null) {
                final Field[] fields = curr.getDeclaredFields();
                final Set<String> excludedForClass = this.excludedFieldNames.get(curr);

                if (excludedForClass != null) {
                    exclusions.addAll(excludedForClass);
                }

                final Set<String> excludedInjectors = this.excludedInjectorFields.get(curr);

                if (excludedInjectors != null) {
                    exclusions.addAll(excludedInjectors);
                }

                for (Field field : fields) {
                    if (Modifier.isStatic(field.getModifiers()) ||
                            exclusions.contains(field.getName()) ||
                            fieldIsFiltered(field)) {
                        continue;
                    }

                    String name = field.getName();

                    if (map.putIfAbsent(name, field) != null) {
                        map.put(field.getDeclaringClass().getSimpleName() + '.' + name, field);
                    }
                }
                curr = curr.getSuperclass();
            }
            return Collections.unmodifiableMap(map);
        }
        
        private boolean fieldIsFiltered(Field field) {
            for (FieldFilter filter : this.fieldFilters) {
                if (filter.filter(field)) {
                    return true;
                }
            }
            return false;
        }
    }
}
