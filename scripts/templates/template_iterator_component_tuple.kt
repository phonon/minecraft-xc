public data class ComponentTuple{{ n }}<
    {%- for ty in types %}
    {{ ty }}: VehicleComponent,
    {%- endfor %}
>(
    val element: VehicleElementId,
    {%- for ty in types %}
    val {{ ty.lower() }}: {{ ty }},
    {%- endfor %}
): ComponentTuple {
    
    companion object {
        /**
         * Note: this could be cached as a Query class.
         * Query would need to support being re-created if engine reloads
         * vehicle element types.
         */
        public inline fun <
            {%- for ty in types %}
            reified {{ ty }}: VehicleComponent,
            {%- endfor %}
        > query(components: ComponentsStorage): ComponentTuple{{ n }}Iterator<{{ types_list }}> {
            val layout = EnumSet.of(
                {%- for ty in types %}
                VehicleComponentType.from<{{ ty }}>(),
                {%- endfor %}
            )
            {% for ty in types %}
            val getStorage{{ ty }}: (ArchetypeStorage) -> ArrayList<{{ ty }}> = ArchetypeStorage.accessor()
            {%- endfor %}

            return ComponentTuple{{ n }}Iterator<{{ types_list }}>(
                layout,
                components,
                {%- for ty in types %}
                getStorage{{ ty }},
                {%- endfor %}
            )
        }
    }
}


class ComponentTuple{{ n }}Iterator<
    {%- for ty in types %}
    {{ ty }}: VehicleComponent,
    {%- endfor %}
>(
    val layout: EnumSet<VehicleComponentType>,
    val components: ComponentsStorage,
    {%- for ty in types %}
    val getStorage{{ ty }}: (ArchetypeStorage) -> ArrayList<{{ ty }}>,
    {%- endfor %}
): Iterator<ComponentTuple{{ n }}<{{ types_list }}>> {
    // get list of matching archetypes. simplifies logic because
    // `hasNext` must always know if there is a "next" valid archetype
    // that needs to be traversed.
    // TODO: this could be cached in a query object that creates the iterator
    val validArchetypes = components.getMatchingArchetypes(layout).filter { it.size > 0 }

    // archetype index within valid archetypes
    var currArchetypeIndex = 0
    
    // current archetype's relevent storages
    var currElementCount: Int = 0
    var currElementIds: IntArray? = null
    {%- for ty in types %}
    var currStorage{{ ty }}: List<{{ ty }}>? = null
    {%- endfor %}
    // index within an archetype's storage
    var elementIndex = 0

    init {
        // initialize with first valid archetype
        if ( validArchetypes.size > 0 ) {
            val archetype = validArchetypes[currArchetypeIndex]
            currElementCount = archetype.size
            currElementIds = archetype.elements
            {%- for ty in types %}
            currStorage{{ ty }} = getStorage{{ ty }}(archetype)
            {%- endfor %}
        }
    }

    override fun hasNext(): Boolean {
        return currArchetypeIndex < validArchetypes.size && elementIndex < currElementCount
    }

    override fun next(): ComponentTuple{{ n }}<{{ types_list }}> {
        val id = currElementIds!![elementIndex]
        {%- for ty in types %}
        val {{ ty.lower() }} = currStorage{{ ty }}!![elementIndex]
        {%- endfor %}
        elementIndex += 1

        // finished with this archetype, move to next
        if ( elementIndex >= currElementCount ) {
            currArchetypeIndex += 1
            
            if ( currArchetypeIndex < validArchetypes.size ) {
                val archetype = validArchetypes[currArchetypeIndex]
                currElementCount = archetype.size
                currElementIds = archetype.elements
                {%- for ty in types %}
                currStorage{{ ty }} = getStorage{{ ty }}(archetype)
                {%- endfor %}
    
                elementIndex = 0
            }
        }
        
        return ComponentTuple{{ n }}(id, {{ types_list.lower() }})
    }
}