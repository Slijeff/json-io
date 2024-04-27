package com.cedarsoftware.io.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Supplier;

/**
 * NOTE: Please do not reformat this code. This is a very lambda-like implementation
 * and it makes it easy to see over all structure.  The individual methods are trivial
 * because this is about APIs and delegating.
 * <br><br>
 * UnmodifiableList provides a List that can be 'sealed' and 'unsealed.  When
 * sealed, the List can be mutated, when unsealed it is read-only. The iterator,
 * listIterator, and subList() return views that honor the original List's
 * sealed state.
 * <br><br>
 * @author John DeRegnaucourt (jdereg@gmail.com)
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
 *         limitations under the License.
 */
public class UnmodifiableList<T> implements List<T> {
    private final List<T> list;
    private final Supplier<Boolean> sealedSupplier;

    public UnmodifiableList(Supplier<Boolean> sealedSupplier) {
        this.list = new ArrayList<>();
        this.sealedSupplier = sealedSupplier;
    }
    public UnmodifiableList(List<T> list, Supplier<Boolean> sealedSupplier) {
        this.list = list;
        this.sealedSupplier = sealedSupplier;
    }

    private void throwIfSealed() {
        if (sealedSupplier.get()) {
            throw new UnsupportedOperationException("This list has been sealed and is now immutable");
        }
    }

    // Immutable APIs
    public boolean equals(Object other) { return list.equals(other); }
    public int hashCode() { return list.hashCode(); }
    public int size() { return list.size(); }
    public boolean isEmpty() { return list.isEmpty(); }
    public boolean contains(Object o) { return list.contains(o); }
    public boolean containsAll(Collection<?> col) { return new HashSet<>(list).containsAll(col); }
    public int indexOf(Object o) { return list.indexOf(o); }
    public int lastIndexOf(Object o) { return list.lastIndexOf(o); }
    public T get(int index) { return list.get(index); }
    public Object[] toArray() { return list.toArray(); }
    public <T1> T1[] toArray(T1[] a) { return list.toArray(a);}
    public Iterator<T> iterator() { return createSealHonoringIterator(list.iterator()); }
    public ListIterator<T> listIterator() { return createSealHonoringListIterator(list.listIterator()); }
    public ListIterator<T> listIterator(final int index) { return createSealHonoringListIterator(list.listIterator(index)); }
    public List<T> subList(int fromIndex, int toIndex) { return new UnmodifiableList<>(list.subList(fromIndex, toIndex), sealedSupplier); }
    
    // Mutable APIs
    public boolean add(T t) { throwIfSealed(); return list.add(t); }
    public boolean remove(Object o) { throwIfSealed(); return list.remove(o); }
    public boolean addAll(Collection<? extends T> col) { throwIfSealed(); return list.addAll(col); }
    public boolean addAll(int index, Collection<? extends T> col) { throwIfSealed(); return list.addAll(index, col); }
    public boolean removeAll(Collection<?> col) { throwIfSealed(); return list.removeAll(col); }
    public boolean retainAll(Collection<?> col) { throwIfSealed(); return list.retainAll(col); }
    public void clear() { throwIfSealed(); list.clear(); }
    public T set(int index, T element) { throwIfSealed(); return list.set(index, element); }
    public void add(int index, T element) { throwIfSealed(); list.add(index, element); }
    public T remove(int index) { throwIfSealed(); return list.remove(index); }

    private Iterator<T> createSealHonoringIterator(Iterator<T> iterator) {
        return new Iterator<T>() {
            public boolean hasNext() { return iterator.hasNext(); }
            public T next() { return iterator.next(); }
            public void remove() { throwIfSealed(); iterator.remove(); }
        };
    }

    private ListIterator<T> createSealHonoringListIterator(ListIterator<T> iterator) {
        return new ListIterator<T>() {
            public boolean hasNext() { return iterator.hasNext();}
            public T next() { return iterator.next(); }
            public boolean hasPrevious() { return iterator.hasPrevious(); }
            public T previous() { return iterator.previous(); }
            public int nextIndex() { return iterator.nextIndex(); }
            public int previousIndex() { return iterator.previousIndex(); }
            public void remove() { throwIfSealed(); iterator.remove(); }
            public void set(T e) { throwIfSealed(); iterator.set(e); }
            public void add(T e) { throwIfSealed(); iterator.add(e);}
        };
    }
}