package io.github.some_example_name.old.systems.node

object ConnectionManager {
    private const val SNAP_RADIUS = 24f

    var highlight: Pair<Node, Node>? = null

    fun onDrag(dragged: Node, nodes: List<Node>) {
        val pair = findCandidate(dragged, nodes)
        highlight = pair

        val (parent, child) = pair ?: return

        child.moveSubtree(
            parent.x + (parent.width - child.width) / 2 - child.x,
            parent.y - child.height - child.y
        )
    }

    fun onDrop(dragged: Node, nodes: List<Node>) {
        val (parent, child) = highlight ?: return

        parent.childNodes.add(child)
        child.parentNode = parent

        highlight = null
    }

    private fun findCandidate(dragged: Node, nodes: List<Node>): Pair<Node, Node>? {
        var best: Pair<Node, Node>? = null
        var bestDist = SNAP_RADIUS

        for (other in nodes) {
            if (other === dragged || other === dragged.ignoreParent) continue
            if (dragged.nodeAction?.nodeData?.finalNode == false && other.canConnectTo(dragged)) {
                val d = dragged.outputSocket.dst(other.inputSocket)

                if (d < bestDist) {
                    bestDist = d
                    best = dragged to other
                }
            }
            if (other.nodeAction?.nodeData?.finalNode == false && dragged.canConnectTo(other)) {
                val d = dragged.inputSocket.dst(other.outputSocket)

                if (d < bestDist) {
                    bestDist = d
                    best = other to dragged
                }
            }
        }

        return best
    }
}
