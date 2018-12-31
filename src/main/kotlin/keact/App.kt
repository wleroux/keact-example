package keact

import keact.FamilyBuilder.Companion.family
import keact.PersonBuilder.Companion.person
import kotlin.reflect.KClass

fun main(args: Array<String>) {
    val node = family {
        +person {
            name = "Hello"
        }
        +person {
            name = "World"
        }

        "test".substring(5)


    }

    val test = "test"
    test.substring(4)
    test.substring(11)


    val rootComponent = node.mount()
    rootComponent.render()

    rootComponent.update(family {
        +person {
            name = "Wayne"
        }
    }.properties)
    rootComponent.render()
}

class FamilyBuilder(private val key: Any?) {
    companion object {
        fun family(key: Any? = null, block: FamilyBuilder.() -> Unit) =
                FamilyBuilder(key).apply(block).build()
    }

    private val nodes: MutableList<Node<*, *>> = mutableListOf()
    operator fun Node<*, *>.unaryPlus() {
        nodes += this
    }

    fun build() =
            Node(key, FamilyComponent::class, nodes)
}

class FamilyComponent: Component<Unit, List<Node<*, *>>>(Unit) {
    override fun asNodes() = properties
}

class PersonBuilder(private val key: Any?) {
    companion object {
        @CheckReturnValue
        fun person(key: Any? = null, block: PersonBuilder.() -> Unit) =
                PersonBuilder(key).apply(block).build()
    }

    lateinit var name: String

    fun build() =
            Node(key, PersonComponent::class, name)
}

class PersonComponent: Component<Unit, String>(Unit) {
    override fun render() {
        println("- $properties")
    }
}

abstract class Component<State: Any, Properties: Any>(initialState: State) {
    lateinit var properties: Properties
    var state: State = initialState

    internal var previousState: State = initialState
    internal var parentComponent: Component<*, *>? = null
    internal var childComponents: Map<Any, Component<*, *>> = emptyMap()

    open fun componentWillMount() = Unit
    open fun componentDidMount() = Unit
    open fun componentWillUnmount() = Unit
    open fun componentWillReceiveProps(nextProperties: Properties) = Unit
    open fun shouldComponentUpdate(nextProperties: Properties, state: State): Boolean {
        return this.properties !== nextProperties || this.previousState !== state
    }
    open fun componentWillUpdate(nextProperties: Properties, state: State) = Unit
    open fun componentDidUpdate(previousProperties: Properties, previousState: State) = Unit
    open fun asNodes(): List<Node<*, *>> = emptyList()
    open fun render() {
        childComponents.values.forEach { childComponent ->
            childComponent.render()
        }
    }
}

data class Node<State: Any, Properties: Any>(
        val key: Any?,
        val type: KClass<out Component<State, Properties>>,
        val properties: Properties
)

fun <State: Any, Properties: Any> Node<State, Properties>.mount(parentComponent: Component<*, *>? = null): Component<State, Properties> {
    val component = this.type.java.newInstance()

    component.componentWillMount()
    component.parentComponent = parentComponent
    component.properties = this.properties
    component.componentDidMount()

    component.update(component.properties, true)

    return component
}

fun <State: Any, Properties: Any> Component<State, Properties>.unmount() {
    childComponents.values.forEach { it.unmount() }
    childComponents = emptyMap()
    componentWillUnmount()
}

fun <State: Any, Properties: Any> Component<State, Properties>.update(nextProperties: Properties, forceUpdate: Boolean = false) {
    fun <NodeState: Any, NodeProperties: Any> nodeComponent(node: Node<NodeState, NodeProperties>, prev: Component<*, *>? = null, parent: Component<*, *>? = null): Component<NodeState, NodeProperties> {
        val reuseComponent = if (prev != null) prev::class == node.type else false
        val nodeComponent = if(reuseComponent) {
            @Suppress("UNCHECKED_CAST")
            prev!! as Component<NodeState, NodeProperties>
        } else {
            node.mount(parent)
        }
        nodeComponent.update(node.properties)
        return nodeComponent
    }
    fun <ChildState: Any, ChildProperties: Any> updateChild(component: Component<ChildState, ChildProperties>) {
        component.update(component.properties)
    }

    val previousProperties = properties
    if (nextProperties !== previousProperties)
        componentWillReceiveProps(nextProperties)
    if (forceUpdate || shouldComponentUpdate(nextProperties, state)) {
        componentWillUpdate(nextProperties, state)
        val previousChildrenComponents = childComponents
        properties = nextProperties

        var keyCounter = 0
        childComponents = asNodes().map { node ->
            val key = node.key ?: keyCounter ++
            val prevChildComponent = previousChildrenComponents[key]
            key to nodeComponent(node, prevChildComponent, this)
        }.toMap()

        previousChildrenComponents.values.minus(childComponents.values).forEach { childComponent ->
            childComponent.unmount()
        }

        componentDidUpdate(previousProperties, previousState)
        previousState = state
    } else {
        properties = nextProperties
        previousState = state
        childComponents.values.forEach { childComponent ->
            updateChild(childComponent)
        }
    }
}