package edu.moravian.markdowneditor.android.utilities

/**
 * Simple wrapper around a list that makes it read-only. This is useful for
 * getting a read-only view of a mutable list to prevent it from being modified
 * even if "as? MutableList" is used.
 */
private class ReadonlyListView<T>(private val list: List<T>): List<T> {
    override val size get() = list.size
    override fun isEmpty() = list.isEmpty()
    override fun get(index: Int) = list[index]
    override fun contains(element: T) = list.contains(element)
    override fun containsAll(elements: Collection<T>) = list.containsAll(elements)
    override fun indexOf(element: T) = list.indexOf(element)
    override fun lastIndexOf(element: T) = list.lastIndexOf(element)
    override fun subList(fromIndex: Int, toIndex: Int) = ReadonlyListView(list.subList(fromIndex, toIndex))
    override fun iterator(): Iterator<T> = listIterator()
    override fun listIterator() = listIterator(0)
    override fun listIterator(index: Int): ListIterator<T> = object : ListIterator<T> {
        private val iter = list.listIterator(index)
        override fun hasPrevious() = iter.hasPrevious()
        override fun hasNext() = iter.hasNext()
        override fun previousIndex() = iter.previousIndex()
        override fun nextIndex() = iter.nextIndex()
        override fun previous() = iter.previous()
        override fun next() = iter.next()
    }

    override fun toString() = list.toString()
    override fun hashCode() = list.hashCode()
    override fun equals(other: Any?) = list == other
}

/** Get a read-only view of a mutable list. */
fun <T> MutableList<T>.asList(): List<T> = ReadonlyListView(this)
