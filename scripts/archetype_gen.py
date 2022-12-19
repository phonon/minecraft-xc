"""
Generates:
- component enum hard-codings in archetype, prototype classes
- ComponentTupleN classes and iterators

Add components in components.py

Uses jinja2, see template documentation:
https://jinja.palletsprojects.com/en/3.1.x/templates/
"""

import os
from jinja2 import Environment, FileSystemLoader, select_autoescape
from components import get_components

DIR_TEMPLATES = os.path.join("scripts", "templates")
DIR_OUTPUT = os.path.join("src", "main", "kotlin", "phonon", "xv")

env = Environment(
    loader=FileSystemLoader(DIR_TEMPLATES),
    autoescape=select_autoescape()
)

components = get_components()

# ==================================================
# archetype, prototype, component inject enum hard-codings
# ==================================================
template_archetype = env.get_template("template_archetype.kt")
template_prototype = env.get_template("template_prototype.kt")
template_serde = env.get_template("template_serde.kt")

archetype_kt = template_archetype.render(
    components=components,
)

prototype_kt = template_prototype.render(
    components=components,
)

serde_kt = template_serde.render(
    components=components
)

# ==================================================
# component tuple N generation
# ==================================================
template_iterator_base = env.get_template("template_iterator.kt")
template_iterator_component_tuple = env.get_template("template_iterator_component_tuple.kt")

# manually specify types
tuple_types = [
    ["A"],
    ["A", "B"],
    ["A", "B", "C"],
    ["A", "B", "C", "D"],
    ["A", "B", "C", "D", "E"],
    ["A", "B", "C", "D", "E", "F"],
    # ["A", "B", "C", "D", "E", "F", "G"],
    # ["A", "B", "C", "D", "E", "F", "G", "H"],
    # ["A", "B", "C", "D", "E", "F", "G", "H", "I"],
    # ["A", "B", "C", "D", "E", "F", "G", "H", "I", "J"],
]
tuple_implementations = []
for types in tuple_types:
    tuple_size = len(types)

    tuple_implementations.append(template_iterator_component_tuple.render(
        n=tuple_size,
        types=types,
        types_list=", ".join(types),
    ))

iterator_kt = template_iterator_base.render(
    tuple_implementations=tuple_implementations,
)

path_iterator_kt = os.path.join(DIR_OUTPUT, "core", "iterator.kt")
with open(path_iterator_kt, "w+") as f:
    print(f"{path_iterator_kt}")
    f.write(iterator_kt)

path_prototype_kt = os.path.join(DIR_OUTPUT, "core", "prototype.kt")
with open(path_prototype_kt, "w+") as f:
    print(f"{path_prototype_kt}")
    f.write(prototype_kt)

path_archetype_kt = os.path.join(DIR_OUTPUT, "core", "archetype.kt")
with open(path_archetype_kt, "w+") as f:
    print(f"{path_archetype_kt}")
    f.write(archetype_kt)

path_serde_kt = os.path.join(DIR_OUTPUT, "core", "serde.kt")
with open(path_serde_kt, "w+") as f:
    print(f"{path_serde_kt}")
    f.write(serde_kt)