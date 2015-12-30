package com.piranha.scanner;

import com.google.gson.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Padmaka on 9/8/2015.
 */
public class Scanner {
    private static final Logger log = Logger.getLogger(Scanner.class);
    private ArrayList<JsonObject> classes;
    private ArrayList<JsonObject> classDetails;

    /***
     * overloaded constructor
     */
    public Scanner() {
        classes = new ArrayList<>();
    }

    /***
     * The method to read all the files found in the given location and input them to the system.
     * @return collection of all the files found in the given location.
     */
    public Collection readFiles() {
        log.debug(System.getProperty("user.dir"));
        File file = new File(System.getProperty("user.dir") + "/src/main/resources");
        Collection files = FileUtils.listFiles(file, new RegexFileFilter("^(.*?)"), DirectoryFileFilter.DIRECTORY);
        return files;
    }

    /***
     * The method to scan through all the source files found and find classes, interfaces, enums and inner classes.
     * @param files
     * @return a Json containing a list of all the classes, interfaces, enums and their details.
     * @throws IOException
     */
    public ArrayList<JsonObject> scan(ArrayList<File> files) throws IOException {
        Gson gson = new Gson();
//        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        //Iterate through all the files
        for (File f : files) {

//            log.debug(f.getName());
            InputStreamReader inputStreamReader = new InputStreamReader(new FileInputStream(f));

            //getting file to a string
            String fileString = FileUtils.readFileToString(f, inputStreamReader.getEncoding());

            //Finding import statements
            JsonArray importStatements = this.findImportStatements(fileString);
//            log.debug(importStatements);

            JsonObject classJson = this.getClass(fileString);

            try {
                if (classJson != null) {
                    this.findOuterClasses(f, classJson.get("classDeclaration").getAsString(), fileString, classJson.get("end").getAsInt() - 1, importStatements);
                }
            } catch (IllegalStateException e) {
                log.error(f.getName() + " - No match found");
            }

        }

        //finding inner classes
        ArrayList<String> innerClasses;

        for (JsonObject classJson : classes) {
            innerClasses = this.findInnerClasses(classJson.get("classString").getAsString());

            JsonArray innerClassesJsonArray = new JsonArray();
            for (String innerClass : innerClasses) {
                innerClassesJsonArray.add(new JsonPrimitive(innerClass));
            }
            classJson.add("innerClasses", innerClassesJsonArray);
        }

//        log.debug(gson.toJson(classes));
        classDetails = this.getFullClassList();
        return classes;
    }

    /***
     * The method to find a class declaration in a given String
     * @param fileString
     * @return Json with class details
     */
    public JsonObject getClass(String fileString) {

//        Pattern pattern = Pattern.compile("(((public|protected|private|)?(\\s+abstract)?(\\s+static)?\\s+class\\s+(\\w+)((\\s+extends\\s+\\w+)|(\\s+implements\\s+\\w+\\s*(,\\s*\\w+\\s*)*)|(\\s+extends\\s+\\w+\\s+implements\\s+\\w+\\s*(,\\s*\\w+\\s*)*))?\\s*\\{)|" +
//                "((public|protected)?(\\s+abstract)?(\\s+static)?\\s+interface\\s+(\\w+)(\\s+extends\\s+\\w+\\s*(,\\s*\\w+\\s*)*)?\\s*\\{))");

//        Pattern pattern = Pattern.compile("(((public|protected|private|)?(\\s+abstract)?(\\s+static)?\\s+class\\s+(\\w+)((\\s+extends\\s+\\w+)|(\\s+implements\\s+\\w+\\s*(,\\s*\\w+\\s*)*)|(\\s+extends\\s+\\w+\\s+implements\\s+\\w+\\s*(,\\s*\\w+\\s*)*))?\\s*\\{)|" +
//                "((public|protected)?(\\s+abstract)?(\\s+static)?\\s+interface\\s+(\\w+)(\\s+extends\\s+\\w+\\s*(,\\s*\\w+\\s*)*)?\\s*\\{)|" +
//                "((public|protected|private)?(\\s+static)?\\s+enum\\s+(\\w+)(\\s+implements\\s+\\w+\\s*(,\\s*\\w+\\s*)*)?\\s*\\{))");

        Pattern pattern = Pattern.compile("(((public|protected|private|)?(\\s+abstract)?(\\s+static)?\\s+class\\s+(\\w+)((\\s+extends\\s+(\\w+\\.)*?\\w+)|(\\s+implements\\s+(\\w+\\.)*?\\w+\\s*(,\\s*\\w+\\s*)*)|(\\s+extends\\s+(\\w+\\.)*?\\w+\\s+implements\\s+(\\w+\\.)*?\\w+\\s*(,\\s*\\w+\\s*)*))?\\s*\\{)|((public|protected)?(\\s+abstract)?(\\s+static)?\\s+interface\\s+(\\w+)(\\s+extends\\s+(\\w+\\.)*?\\w+\\s*(,\\s*\\w+\\s*)*)?\\s*\\{)|((public|protected|private)?(\\s+static)?\\s+enum\\s+(\\w+)(\\s+implements\\s+(\\w+\\.)*?\\w+\\s*(,\\s*\\w+\\s*)*)?\\s*\\{))");

        Matcher matcher = pattern.matcher(fileString);

        JsonObject classJson = null;
        try {
            matcher.find();
            String classDeclaration = matcher.group();

            classJson = new JsonObject();
            classJson.addProperty("end", matcher.end());
            classJson.addProperty("classDeclaration", classDeclaration);
        } catch (IllegalStateException e) {
            return null;
        }


        return classJson;
    }

    /***
     * The method to find all outer classes in a given .java file string.
     * @param file
     * @param className
     * @param fileString
     * @param startOfClass
     * @param importStatements
     */
    public void findOuterClasses(File file, String className, String fileString, int startOfClass, JsonArray importStatements) {
        String classDeclaration = className;
//        int endOfClass = 0;
        Stack<Character> stack = new Stack<Character>();

//        log.debug(fileString);

        for (int i = startOfClass; i < fileString.length(); i++) {

            char current = fileString.charAt(i);
            if (current == '{') {
                stack.push(current);
            }
            if (current == '}') {
                char last = stack.peek();
                if (current == '}' && last == '{') {
                    stack.pop();
                } else {
//                    endOfClass = -1;
                }

                if (stack.isEmpty()) {
//                    endOfClass = i;
                    String classString = fileString.substring(startOfClass + 1, i);
                    JsonObject classJson = new JsonObject();
                    classJson.addProperty("file", file.getName());
                    classJson.addProperty("filePath", file.getPath());

                    className = className.replace("public", "");
                    className = className.replace("protected", "");
                    className = className.replace("private", "");
                    className = className.replace("abstract", "");
                    className = className.replace("static", "");
                    className = className.replace("class", "");
                    className = className.replace("interface", "");
                    className = className.replace("enum", "");
                    className = className.replace("{", "");
                    className = className.trim();
                    String[] classNameParts = className.split(" ");
                    className = classNameParts[0];

                    classJson.addProperty("className", className);
                    classJson.addProperty("classDeclaration", classDeclaration.trim());
                    classJson.addProperty("classString", classString.trim());
                    classJson.add("importStatements", importStatements);
                    classes.add(classJson);

                    if (i + 1 < fileString.length()) {
                        String restOfTheString = fileString.substring(i, fileString.length());
                        JsonObject nextClass = this.getClass(restOfTheString);
                        if (nextClass != null) {
                            this.findOuterClasses(file, nextClass.get("classDeclaration").getAsString(), restOfTheString, nextClass.get("end").getAsInt() - 1, importStatements);
                        }
                    }
                    break;
                }
            }
        }
    }

    /***
     * The method to find all the names of inner classes in a given class body string
     * @param classString
     * @return List of strings containing the names of inner classes.
     */
    public ArrayList<String> findInnerClasses(String classString) {
        ArrayList<String> innerClasses = new ArrayList<>();
        JsonObject innerClass = new JsonObject();
        String classDeclaration = null;
        int end = 0;

        while (innerClass != null){
            innerClass = this.getClass(classString);
            if (innerClass != null) {
                classDeclaration = innerClass.get("classDeclaration").getAsString();
                end = innerClass.get("end").getAsInt();

                classDeclaration = classDeclaration.replace("public", "");
                classDeclaration = classDeclaration.replace("protected", "");
                classDeclaration = classDeclaration.replace("private", "");
                classDeclaration = classDeclaration.replace("abstract", "");
                classDeclaration = classDeclaration.replace("static", "");
                classDeclaration = classDeclaration.replace("class", "");
                classDeclaration = classDeclaration.replace("interface", "");
                classDeclaration = classDeclaration.replace("enum", "");
                classDeclaration = classDeclaration.replace("{", "");
                classDeclaration = classDeclaration.trim();
                String[] classNameParts = classDeclaration.split(" ");
                classDeclaration = classNameParts[0];

                innerClasses.add(classDeclaration.trim());
                classString = classString.substring(end, classString.length() - 1);
            }
        }

        return innerClasses;
    }

    /***
     * The method to get a full list of names of the classes, interfaces, enums and inner classes with their details
     * @return  list of Json objects with full list of all the classes, interfaces, enums, and inners classes along
     *          with outer class name and package name.
     */
    public ArrayList<JsonObject> getFullClassList(){
        ArrayList<JsonObject> classList = new ArrayList<>();
        String rootPath = System.getProperty("user.dir")+"/src/main/resources/";

        for (JsonObject classJson : classes) {
            String packageName = classJson.get("filePath").getAsString().replace(rootPath, "");
            packageName = packageName.replace("/", ".");
            packageName = packageName.replace(classJson.get("file").getAsString(), "");
//            log.debug(packageName);

            JsonObject tempClassJson = new JsonObject();
            tempClassJson.addProperty("packageName", packageName);
            tempClassJson.addProperty("className", classJson.get("className").getAsString());
            tempClassJson.addProperty("outerClass", classJson.get("className").getAsString());
            classList.add(tempClassJson);
            if(classJson.get("innerClasses").getAsJsonArray().size() > 0) {
                for (JsonElement innerClass : classJson.get("innerClasses").getAsJsonArray()){
                    JsonObject tempInnerClassJson = new JsonObject();
                    tempInnerClassJson.addProperty("packageName", packageName);
                    tempInnerClassJson.addProperty("className", innerClass.getAsString());
                    tempInnerClassJson.addProperty("outerClass", classJson.get("className").getAsString());
                    classList.add(tempInnerClassJson);
                }
            }
        }

        return classList;
    }

    /***
     * The method to find the import statements in a given .java file string.
     * @param fileString
     * @return Json array of all the import statements.
     */
    public JsonArray findImportStatements(String fileString) {
        Pattern pattern = Pattern.compile("import(\\s+static)?\\s+(\\w+\\.)*(\\w+|\\*)");

        Matcher matcher = pattern.matcher(fileString);
        JsonArray importStatemetns = new JsonArray();

        try {
            while (matcher.find()) {
                importStatemetns.add(new JsonPrimitive(matcher.group()));
            }
        } catch (IllegalStateException e) {
            return null;
        }

        return importStatemetns;
    }

    /***
     * The method to remove any comments or String literals in a given class string.
     * @param classString
     * @return class string without any comments or string literals.
     */
    public String removeCommentsAndStrings(String classString) {
        return classString.replaceAll("((['\"])(?:(?!\\2|\\\\).|\\\\.)*\\2)|\\/\\/[^\\n]*|\\/\\*(?:[^*]|\\*(?!\\/))*\\*\\/", "");
    }

    /***
     * The method to find the dependencies for all the classes in the source code.
     */
    public void findDependencies() {

        for (JsonObject classJson : classes) {
            String className = classJson.get("className").getAsString();
            String classDeclaration = classJson.get("classDeclaration").getAsString();
            String classString = classJson.get("classString").getAsString();
            JsonArray innerClasses = classJson.get("innerClasses").getAsJsonArray();

            JsonArray importStatements = classJson.get("importStatements").getAsJsonArray();

            Set<String> dependencies = new HashSet<>();

            for (JsonObject classDetailsJson : classDetails) {
                String packageName = classDetailsJson.get("packageName").getAsString();
                String checkingClassName = classDetailsJson.get("className").getAsString();
                String outerClass = classDetailsJson.get("outerClass").getAsString();

                //checking for dependencies in the class declaration
                classDeclaration = classDeclaration.replace(className, "");
                if (classDeclaration.contains(" "+checkingClassName)) {
                    log.debug(className + " is dependent on " + outerClass);
                    dependencies.add(outerClass);
                }
                if (classDeclaration.contains(packageName + checkingClassName)) {
                    log.debug(className + " is dependent on " + packageName+outerClass);
                    dependencies.add(packageName+outerClass);
                }

                //checking for dependencies in the class body
                String cleanedUpClassString = this.removeCommentsAndStrings(classString);
                cleanedUpClassString = cleanedUpClassString.replace(className, "");
                for (JsonElement innerClass : innerClasses) {
                    cleanedUpClassString = cleanedUpClassString.replace(innerClass.getAsString(), "");
                }
//                log.debug(cleanedUpClassString);

                if (cleanedUpClassString.contains(" "+checkingClassName)) {
                    dependencies.add(outerClass);
                    log.debug(className + " is dependent on " + outerClass);
                }
                if (cleanedUpClassString.contains(packageName + checkingClassName)) {
                    dependencies.add(outerClass);
                    log.debug(className + " is dependent on " + packageName+outerClass);
                }


                //In the end add dependencies to classes json
                JsonArray dependencyJsonArray = new JsonArray();

                for (String dependency : dependencies) {
                    dependencyJsonArray.add(new JsonPrimitive(dependency));
                }

                classJson.add("dependencies", dependencyJsonArray);
            }
        }

        log.debug(classes);
    }
}