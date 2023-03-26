"""
Generates documentation for components:
- toml config example resource files: `example/component_name.toml`
- mdbook documentation: TODO

Convention:
component files are located in:
    src/main/kotlin/phonon/xv/component/{ENUM.lowercase()}.kt

Does very basic text parsing of component file block comment,
class arguments and comments to generate documentation.

Custom component parsing tokens:

@skip
    Skip the current comment or property line

@skipall
    Skip all remaining property lines. Useful for splitting
    class arguments into config section and non-config section:
        public data class TransformComponent(
            // offset from parent element
            val offsetX: Double = 0.0,
            val offsetZ: Double = 0.0,

            // @skipall
            // RUNTIME MOTION STATE BELOW, NOT CONFIG OPTIONS
            var x: Double = 0.0,
            var z: Double = 0.0,
        ): VehicleComponent {

@prop name = value
    Treat the line as a config property, replaces line in toml
    with string after @prop, e.g. "name = value". Use case is,
    some properties in java class are split into separate arguments, e.g.
        val offsetX: Double = 0.0,
        val offsetY: Double = 1.0,
        val offsetZ: Double = 0.0,
    but in the .toml config we want to just use a single line:
        offset = [0.0, 1.0, 0.0]
    This @prop token allows us to just add custom property,
    then add // @skip comment in property lines to skip them:
        public data class ModelComponent(
            // armor stand local offset
            // @prop offset = [0.0, 0.0, 0.0]
            val offsetX: Double = 0.0, // @skip
            val offsetY: Double = 0.0, // @skip
            val offsetZ: Double = 0.0, // @skip
"""

import os
import re
from jinja2 import Environment, FileSystemLoader, select_autoescape
from components import get_components, ComponentType

DIR_TEMPLATES = os.path.join("scripts", "templates")
DIR_OUTPUT_EXAMPLE_COMPONENTS = os.path.join("src", "main", "resources", "example")
DIR_OUTPUT_DOCUMENTATION = os.path.join("docs", "component")
DIR_SOURCE_COMPONENT_KT = os.path.join("src", "main", "kotlin", "phonon", "xv", "component")

os.makedirs(DIR_OUTPUT_EXAMPLE_COMPONENTS, exist_ok=True)
os.makedirs(DIR_OUTPUT_DOCUMENTATION, exist_ok=True)

env = Environment(
    loader=FileSystemLoader(DIR_TEMPLATES),
    autoescape=select_autoescape()
)

standard_header = \
"""# THIS IS A GENERATED COMPONENT CONFIG EXAMPLE FILE THAT SHOWS
# THIS COMPONENT'S CONFIG OPTIONS.
"""

camel_to_snake_pattern = re.compile(r"(?<!^)(?=[A-Z])")
def camel_to_snake(s: str) -> str:
    """Convert camel case to snake case
    https://stackoverflow.com/questions/1175208/elegant-python-function-to-convert-camelcase-to-snake-case
    """
    return camel_to_snake_pattern.sub("_", s).lower()


def parse_component_files_into_examples(components: list[ComponentType]):
    for component in components:
        path_component = os.path.join(DIR_SOURCE_COMPONENT_KT, component.filename)
        print(path_component)
        
        if not os.path.exists(path_component):
            print(f"-> {component.enum} source does not exist: {path_component}")
            continue

        with open(path_component, "r") as f:
            component_kt = f.read()
            
            # find component location in source file
            x_comp = component_kt.find(component.classname)

            # ============================================================
            # PARSE COMPONENT BLOCK COMMENT AND CONVERT INTO TOML COMMENT
            # ============================================================
            x_comp_comment_start = component_kt[0:x_comp].rfind("/**")
            x_comp_comment_end = component_kt[0:x_comp].rfind("*/")
            if x_comp_comment_start != -1 and x_comp_comment_end != -1:
                header_comment = component_kt[x_comp_comment_start:x_comp_comment_end]
                header_comment = header_comment[4:-3] # strips the /**\n and \n*/
                header_comment = header_comment.replace(" *", "#")
            else:
                header_comment = ""

            # ============================================================
            # PARSE COMPONENT CONFIG PROPERTIES FROM CLASS ARGUMENTS
            # ============================================================
            # find start/stop of class arguments
            x_comp_args_start = component_kt[x_comp:].find("(") # args start
            x_comp_args_end = component_kt[x_comp:].find("{")   # class block start
            component_args = component_kt[x_comp+x_comp_args_start+1:x_comp+x_comp_args_end]
            component_args = [x.strip() for x in component_args.split("\n")]
            
            config_props = []

            # iterating manually by index because some cases need to parse
            # multiple lines and manually move iterator forward
            i = 0
            while i < len(component_args):
                s = component_args[i]
                i += 1

                if "@skipall" in s:
                    break
                elif "@skip" in s:
                    continue
                elif "@prop" in s:
                    # print(f"PROP: {s}")
                    s_prop_start = s.find("@prop") + len("@prop ")
                    config_props.append(s[s_prop_start:])
                elif s.startswith("//"):
                    # HANDLE COMMENT
                    # print(f"COMMENT: {s}")
                    s = s.replace("//", "#")
                    config_props.append(s)
                elif "var" in s or "val" in s:
                    # HANDLE PROPERTY
                    s_chunks = re.split(" |: |:|,", s)
                    s_chunks = list(filter(None, s_chunks))
                    # if s contains a "(" opening bracket, assume opening a
                    # multiline value, search until find ")"
                    if "(" in s and ")" not in s:
                        n = i + 1
                        while i < len(component_args):
                            # reached end, if this is not same line, add a ")" closing bracket
                            if ")" in component_args[i]:
                                s_chunks.append(")")
                                break
                            # else: add line to value chunk
                            s_chunks.append("    " + component_args[i])
                            i += 1
                    
                    # print(f"PROPERTY: {s_chunks}")

                    try:
                        # format is:
                        # [name] [type] = [value]
                        idx_equals = s_chunks.index("=")
                        name = s_chunks[idx_equals - 2]
                        value = "\n".join(s_chunks[idx_equals + 1:])
                        ty = s_chunks[idx_equals - 1] # type
                        
                        # some types need special formatting to remove kotlin
                        # specific syntax, e.g. floats have 0.0f format that 
                        # needs to be stripped
                        s_formatted = format_config_prop(ty, name, value)
                        if s_formatted is not None:
                            config_props.append(s_formatted)
                        
                    except ValueError:
                        print(f"[{component.classname}] WARNING: No default value, skipping: {s}")
                else:
                    pass # skip
            
            # POST-PROCESSING
            # add newline when going from properties to a new comment block
            for i in range(len(config_props)):
                if i > 0 and config_props[i].startswith("#"):
                    if not config_props[i-1].startswith("#"):
                        config_props[i-1] = config_props[i-1] + "\n"

            # ============================================================
            # GENERATE TOML CONFIG
            # ============================================================
            # format of .toml file:
            """
            # STANDARD HEADER

            # COMPONENT HEADER COMMENT
            # ...
            # ...

            [component_name]
            # comment about prop0
            prop0 = 0
            # comment about prop1, skip comment about prop2
            prop1 = 1
            prop2 = 2
            """
            component_toml = \
                standard_header + "\n" \
                + header_comment + "\n\n" \
                + f"[{component.config_name}]" + "\n\n" \
                + "\n".join(config_props) 

            with(open(os.path.join(DIR_OUTPUT_EXAMPLE_COMPONENTS, f"{component.config_name}.toml"), "w+")) as f:
                f.write(component_toml)


# lambdas for replacing array types
# very fragile...fix if need more flexibility for DoubleArray() style constructors
KOTLIN_ARRAY_CONVERSIONS = {
    "BooleanArray": lambda s: s.replace("booleanArrayOf(", "[").replace(")", "]"),
    "IntArray": lambda s: s.replace("intArrayOf(", "[").replace(")", "]"),
    "LongArray": lambda s: s.replace("longArrayOf(", "[").replace(")", "]"),
    "FloatArray": lambda s: s.replace("floatArrayOf(", "[").replace(")", "]"),
    "DoubleArray": lambda s: s.replace("doubleArrayOf(", "[").replace(")", "]"),
}

# skip these variable types, not configurable types
SKIP_CONFIG_VARIABLE_TYPES = {
    "Entity",
    "World",
}

def format_config_prop(
    ty: str,
    name: str,
    val: str,
) -> str:
    """
    Some types need special formatting:
    - kotlin specific syntax, e.g. floats have 0.0f format that 
    needs to be stripped.
    - some properties are intended as runtime args but are not actual
    config options, e.g. Entity or World properties
    """
    name = camel_to_snake(name)

    # strip nullable "?" from type
    ty = ty.replace("?", "")

    if ty in SKIP_CONFIG_VARIABLE_TYPES:
        return None
    elif ty in KOTLIN_ARRAY_CONVERSIONS:
        val = KOTLIN_ARRAY_CONVERSIONS[ty](val)
    elif ty == "Float":
        val = val[:-1] # strips 'f'
    
    return f"{name} = {val}"


if __name__ == "__main__":
    components = get_components()
    parse_component_files_into_examples(components)