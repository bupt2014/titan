package com.thinkaurelius.titan.hadoop.formats.edgelist.rdf;

import com.thinkaurelius.titan.hadoop.FaunusEdge;
import com.thinkaurelius.titan.hadoop.FaunusElement;
import com.thinkaurelius.titan.hadoop.FaunusVertex;
import com.tinkerpop.blueprints.impls.sail.SailTokens;

import org.apache.hadoop.conf.Configuration;
import org.apache.log4j.Logger;
import org.openrdf.model.Literal;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandler;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParser;
import org.openrdf.rio.Rio;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;


/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class RDFBlueprintsHandler implements RDFHandler, Iterator<FaunusElement> {

    private final Logger logger = Logger.getLogger(RDFBlueprintsHandler.class);
    private final boolean useFragments;
    private final Configuration configuration;
    private final Set<String> asProperties = new HashSet<String>();
    private final boolean literalAsProperty;
    private static final String BASE_URI = "http://thinkaurelius.com#";

    private RDFParser parser;
    private final Queue<FaunusElement> queue = new LinkedList<FaunusElement>();
    public static final Map<String, RDFFormat> formats = new HashMap<String, RDFFormat>();

    private static Map<String, Character> dataTypeToClass = new HashMap<String, Character>();

    private static final char STRING = 's';
    private static final char INTEGER = 'i';
    private static final char FLOAT = 'f';
    private static final char DOUBLE = 'd';
    private static final char LONG = 'l';
    private static final char BOOLEAN = 'b';

    static {
        dataTypeToClass.put(SailTokens.XSD_NS + "string", STRING);
        dataTypeToClass.put(SailTokens.XSD_NS + "int", INTEGER);
        dataTypeToClass.put(SailTokens.XSD_NS + "integer", INTEGER);
        dataTypeToClass.put(SailTokens.XSD_NS + "float", FLOAT);
        dataTypeToClass.put(SailTokens.XSD_NS + "double", DOUBLE);
        dataTypeToClass.put(SailTokens.XSD_NS + "long", LONG);
        dataTypeToClass.put(SailTokens.XSD_NS + "boolean", BOOLEAN);
    }

    static {
        formats.put("rdf-xml", RDFFormat.RDFXML);
        formats.put("n-triples", RDFFormat.NTRIPLES);
        formats.put("turtle", RDFFormat.TURTLE);
        formats.put("n3", RDFFormat.N3);
        formats.put("trix", RDFFormat.TRIX);
        formats.put("trig", RDFFormat.TRIG);
        //formats.put("n-quads", NQuadsFormat.NQUADS);
    }

    public RDFBlueprintsHandler(final Configuration configuration) throws IOException {
        this.configuration = configuration;
        this.useFragments = configuration.getBoolean(RDFInputFormat.FAUNUS_GRAPH_INPUT_RDF_USE_LOCALNAME, false);
        this.literalAsProperty = configuration.getBoolean(RDFInputFormat.FAUNUS_GRAPH_INPUT_RDF_LITERAL_AS_PROPERTY, false);
        for (final String property : configuration.getStringCollection(RDFInputFormat.FAUNUS_GRAPH_INPUT_RDF_AS_PROPERTIES)) {
            this.asProperties.add(property.trim());
        }
        this.parser = Rio.createParser(formats.get(configuration.get(RDFInputFormat.FAUNUS_GRAPH_INPUT_RDF_FORMAT)));
        this.parser.setRDFHandler(this);
        this.parser.setDatatypeHandling(RDFParser.DatatypeHandling.IGNORE);
    }

    public void startRDF() throws RDFHandlerException {
        // Do nothing
    }

    public void endRDF() throws RDFHandlerException {
        // Do nothing
    }

    public void handleNamespace(String s, String s1) throws RDFHandlerException {
        // Do nothing
    }

    public String postProcess(final Value resource) {
        if (resource instanceof URI) {
            if (this.useFragments) {
                return ((URI) resource).getLocalName();
            } else {
                return resource.stringValue();
            }
        } else {
            return resource.stringValue();
        }
    }

    private static Object castLiteral(final Literal literal) {
        if (null != literal.getDatatype()) {
            final Character type = dataTypeToClass.get(literal.getDatatype().stringValue());
            if (null == type)
                return literal.getLabel();
            else {
                if (STRING == type) {
                    return literal.getLabel();
                } else if (FLOAT == type) {
                    return Float.valueOf(literal.getLabel());
                } else if (INTEGER == type) {
                    return Integer.valueOf(literal.getLabel());
                } else if (DOUBLE == type) {
                    return Double.valueOf(literal.getLabel());
                } else if (LONG == type) {
                    return Long.valueOf(literal.getLabel());
                } else if (BOOLEAN == type) {
                    return Boolean.valueOf(literal.getLabel());
                } else {
                    return literal.getLabel();
                }
            }
        } else {
            return literal.getLabel();
        }
    }

    public void handleStatement(final Statement s) throws RDFHandlerException {
        if (this.asProperties.contains(s.getPredicate().toString())) {
            final FaunusVertex subject = new FaunusVertex(this.configuration, Crc64.digest(s.getSubject().stringValue().getBytes()));
            subject.setProperty(postProcess(s.getPredicate()), postProcess(s.getObject()));
            subject.setProperty(RDFInputFormat.URI, s.getSubject().stringValue());
            if (this.useFragments)
                subject.setProperty(RDFInputFormat.NAME, postProcess(s.getSubject()));
            this.queue.add(subject);
        } else if (this.literalAsProperty && (s.getObject() instanceof Literal)) {
            final FaunusVertex subject = new FaunusVertex(this.configuration, Crc64.digest(s.getSubject().stringValue().getBytes()));
            subject.setProperty(postProcess(s.getPredicate()), castLiteral((Literal) s.getObject()));
            subject.setProperty(RDFInputFormat.URI, s.getSubject().stringValue());
            if (this.useFragments)
                subject.setProperty(RDFInputFormat.NAME, postProcess(s.getSubject()));
            this.queue.add(subject);
        } else {
            long subjectId = Crc64.digest(s.getSubject().stringValue().getBytes());
            final FaunusVertex subject = new FaunusVertex(this.configuration, subjectId);
            subject.setProperty(RDFInputFormat.URI, s.getSubject().stringValue());
            if (this.useFragments)
                subject.setProperty(RDFInputFormat.NAME, postProcess(s.getSubject()));
            this.queue.add(subject);

            long objectId = Crc64.digest(s.getObject().stringValue().getBytes());
            final FaunusVertex object = new FaunusVertex(this.configuration, objectId);
            object.setProperty(RDFInputFormat.URI, s.getObject().stringValue());
            if (this.useFragments)
                object.setProperty(RDFInputFormat.NAME, postProcess(s.getObject()));
            this.queue.add(object);

            final FaunusEdge predicate = new FaunusEdge(this.configuration, -1, subjectId, objectId, postProcess(s.getPredicate()));
            predicate.setProperty(RDFInputFormat.URI, s.getPredicate().stringValue());
            if (null != s.getContext())
                predicate.setProperty(RDFInputFormat.CONTEXT, s.getContext().stringValue());
            // TODO predicate.enablePath(this.enablePath);
            this.queue.add(predicate);
        }
    }

    public void handleComment(String s) throws RDFHandlerException {
        // Do nothing
    }

    public boolean parse(final String string) throws IOException {
        if (null == string)
            return false;
        try {
            this.parser.parse(new StringReader(string), BASE_URI);
            return true;
        } catch (Exception e) {
            this.logger.error(e.getMessage());
            return false;
        }
    }

    public FaunusElement next() {
        if (this.queue.isEmpty())
            return null;
        else
            return this.queue.remove();
    }

    public boolean hasNext() {
        return !this.queue.isEmpty();
    }

    public void remove() {
        throw new UnsupportedOperationException();
    }
}