package com.thinkaurelius.titan.graphdb.database;

import com.thinkaurelius.titan.diskstorage.keycolumnvalue.Entry;
import com.thinkaurelius.titan.graphdb.relations.RelationCache;
import com.thinkaurelius.titan.graphdb.types.TypeInspector;

/**
 * @author Matthias Broecheler (me@matthiasb.com)
 */
public interface RelationReader {

    public RelationCache parseRelation(long vertexid, Entry data, boolean parseHeaderOnly, TypeInspector tx);

}