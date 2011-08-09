//$HeadURL$
/*----------------------------------------------------------------------------
 This file is part of deegree, http://deegree.org/
 Copyright (C) 2001-2009 by:
 - Department of Geography, University of Bonn -
 and
 - lat/lon GmbH -

 This library is free software; you can redistribute it and/or modify it under
 the terms of the GNU Lesser General Public License as published by the Free
 Software Foundation; either version 2.1 of the License, or (at your option)
 any later version.
 This library is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 details.
 You should have received a copy of the GNU Lesser General Public License
 along with this library; if not, write to the Free Software Foundation, Inc.,
 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

 Contact information:

 lat/lon GmbH
 Aennchenstr. 19, 53177 Bonn
 Germany
 http://lat-lon.de/

 Department of Geography, University of Bonn
 Prof. Dr. Klaus Greve
 Postfach 1147, 53001 Bonn
 Germany
 http://www.geographie.uni-bonn.de/deegree/

 e-mail: info@deegree.org
 ----------------------------------------------------------------------------*/
package org.deegree.feature.persistence.sql;

import static org.deegree.commons.utils.JDBCUtils.close;
import static org.deegree.commons.xml.CommonNamespaces.OGCNS;
import static org.deegree.commons.xml.CommonNamespaces.XLNNS;
import static org.deegree.commons.xml.CommonNamespaces.XSINS;
import static org.slf4j.LoggerFactory.getLogger;

import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import javax.xml.namespace.QName;

import org.deegree.commons.config.DeegreeWorkspace;
import org.deegree.commons.config.ResourceInitException;
import org.deegree.commons.jdbc.ConnectionManager;
import org.deegree.commons.jdbc.ResultSetIterator;
import org.deegree.commons.tom.TypedObjectNode;
import org.deegree.commons.tom.primitive.BaseType;
import org.deegree.commons.tom.primitive.PrimitiveType;
import org.deegree.commons.tom.primitive.PrimitiveValue;
import org.deegree.commons.tom.primitive.SQLValueMangler;
import org.deegree.commons.tom.sql.ParticleConverter;
import org.deegree.commons.utils.JDBCUtils;
import org.deegree.commons.utils.Pair;
import org.deegree.cs.coordinatesystems.ICRS;
import org.deegree.feature.Feature;
import org.deegree.feature.Features;
import org.deegree.feature.persistence.FeatureStore;
import org.deegree.feature.persistence.FeatureStoreException;
import org.deegree.feature.persistence.FeatureStoreGMLIdResolver;
import org.deegree.feature.persistence.FeatureStoreManager;
import org.deegree.feature.persistence.FeatureStoreTransaction;
import org.deegree.feature.persistence.cache.BBoxCache;
import org.deegree.feature.persistence.cache.FeatureStoreCache;
import org.deegree.feature.persistence.cache.SimpleFeatureStoreCache;
import org.deegree.feature.persistence.lock.DefaultLockManager;
import org.deegree.feature.persistence.lock.LockManager;
import org.deegree.feature.persistence.query.CombinedResultSet;
import org.deegree.feature.persistence.query.FeatureResultSet;
import org.deegree.feature.persistence.query.FilteredFeatureResultSet;
import org.deegree.feature.persistence.query.IteratorResultSet;
import org.deegree.feature.persistence.query.MemoryFeatureResultSet;
import org.deegree.feature.persistence.query.Query;
import org.deegree.feature.persistence.sql.blob.BlobCodec;
import org.deegree.feature.persistence.sql.blob.BlobMapping;
import org.deegree.feature.persistence.sql.blob.FeatureBuilderBlob;
import org.deegree.feature.persistence.sql.config.AbstractMappedSchemaBuilder;
import org.deegree.feature.persistence.sql.converter.CustomParticleConverter;
import org.deegree.feature.persistence.sql.converter.FeatureParticleConverter;
import org.deegree.feature.persistence.sql.id.FIDMapping;
import org.deegree.feature.persistence.sql.id.IdAnalysis;
import org.deegree.feature.persistence.sql.jaxb.CustomConverterJAXB;
import org.deegree.feature.persistence.sql.jaxb.SQLFeatureStoreJAXB;
import org.deegree.feature.persistence.sql.rules.CompoundMapping;
import org.deegree.feature.persistence.sql.rules.FeatureBuilderRelational;
import org.deegree.feature.persistence.sql.rules.FeatureMapping;
import org.deegree.feature.persistence.sql.rules.GeometryMapping;
import org.deegree.feature.persistence.sql.rules.Mapping;
import org.deegree.feature.persistence.sql.rules.Mappings;
import org.deegree.feature.persistence.sql.rules.PrimitiveMapping;
import org.deegree.feature.types.FeatureType;
import org.deegree.feature.types.property.GeometryPropertyType;
import org.deegree.feature.types.property.GeometryPropertyType.CoordinateDimension;
import org.deegree.feature.types.property.GeometryPropertyType.GeometryType;
import org.deegree.filter.Filter;
import org.deegree.filter.FilterEvaluationException;
import org.deegree.filter.IdFilter;
import org.deegree.filter.OperatorFilter;
import org.deegree.filter.expression.PropertyName;
import org.deegree.filter.sort.SortProperty;
import org.deegree.filter.spatial.BBOX;
import org.deegree.geometry.Envelope;
import org.deegree.geometry.Geometry;
import org.deegree.geometry.GeometryTransformer;
import org.deegree.gml.GMLObject;
import org.deegree.gml.GMLReferenceResolver;
import org.deegree.sqldialect.SQLDialect;
import org.deegree.sqldialect.filter.AbstractWhereBuilder;
import org.deegree.sqldialect.filter.DBField;
import org.deegree.sqldialect.filter.Join;
import org.deegree.sqldialect.filter.MappingExpression;
import org.deegree.sqldialect.filter.PropertyNameMapper;
import org.deegree.sqldialect.filter.PropertyNameMapping;
import org.deegree.sqldialect.filter.TableAliasManager;
import org.deegree.sqldialect.filter.UnmappableException;
import org.deegree.sqldialect.filter.expression.SQLArgument;
import org.slf4j.Logger;

/**
 * {@link FeatureStore} that is backed by a spatial SQL database.
 * 
 * @see SQLDialect
 * 
 * @author <a href="mailto:schneider@lat-lon.de">Markus Schneider</a>
 * @author last edited by: $Author$
 * 
 * @version $Revision$, $Date$
 */
public class SQLFeatureStore implements FeatureStore {

    private static final Logger LOG = getLogger( SQLFeatureStore.class );

    private final SQLFeatureStoreJAXB config;

    private final URL configURL;

    private final SQLDialect dialect;

    private final boolean allowInMemoryFiltering;

    private MappedApplicationSchema schema;

    private TransactionManager taManager;

    private BlobMapping blobMapping;

    private String jdbcConnId;

    private final Map<Mapping, ParticleConverter<?>> particeMappingToConverter = new HashMap<Mapping, ParticleConverter<?>>();

    // TODO make this configurable
    private final FeatureStoreCache cache = new SimpleFeatureStoreCache( 10000 );

    private BBoxCache bboxCache;

    private final FeatureStoreGMLIdResolver resolver = new FeatureStoreGMLIdResolver( this );

    // cache for feature type bounding boxes
    // private final Map<FeatureType, Envelope> ftToBBox = Collections.synchronizedMap( new HashMap<FeatureType,
    // Envelope>() );

    private Map<String, String> nsContext;

    private DefaultLockManager lockManager;

    private DeegreeWorkspace workspace;

    /**
     * Creates a new {@link SQLFeatureStore} for the given configuration.
     * 
     * @param config
     *            jaxb configuration object
     * @param configURL
     *            configuration systemid
     */
    public SQLFeatureStore( SQLFeatureStoreJAXB config, URL configURL, SQLDialect dialect ) {
        this.config = config;
        this.configURL = configURL;
        this.dialect = dialect;
        this.jdbcConnId = config.getJDBCConnId();
        this.allowInMemoryFiltering = config.getDisablePostFiltering() == null;
    }

    @Override
    public void init( DeegreeWorkspace workspace )
                            throws ResourceInitException {

        LOG.debug( "init" );

        MappedApplicationSchema schema;
        try {
            schema = AbstractMappedSchemaBuilder.build( configURL.toString(), config, dialect );
        } catch ( Throwable t ) {
            LOG.error( t.getMessage(), t );
            throw new ResourceInitException( t.getMessage(), t );
        }

        // lockManager = new DefaultLockManager( this, "LOCK_DB" );

        this.workspace = workspace;
        this.schema = schema;
        this.blobMapping = schema.getBlobMapping();
        taManager = new TransactionManager( this, getConnId() );
        initConverters();
        try {
            // however TODO it properly on the DB
            lockManager = new DefaultLockManager( this, "LOCK_DB" );
        } catch ( Throwable e ) {
            LOG.warn( "Lock manager initialization failed, locking will not be available." );
            LOG.trace( "Stack trace:", e );
        }

        // TODO make this configurable
        FeatureStoreManager fsMgr = workspace.getSubsystemManager( FeatureStoreManager.class );
        if ( fsMgr != null ) {
            this.bboxCache = fsMgr.getBBoxCache();
        } else {
            LOG.warn( "Unmanaged feature store." );
        }
    }

    private void initConverters() {
        for ( FeatureType ft : schema.getFeatureTypes() ) {
            FeatureTypeMapping ftMapping = schema.getFtMapping( ft.getName() );
            if ( ftMapping != null ) {
                for ( Mapping particleMapping : ftMapping.getMappings() ) {
                    initConverter( particleMapping );
                }
            }
        }
    }

    private void initConverter( Mapping particleMapping ) {
        if ( particleMapping instanceof PrimitiveMapping ) {
            PrimitiveMapping pm = (PrimitiveMapping) particleMapping;
            ParticleConverter<?> converter = null;
            if ( pm.getConverter() == null ) {
                converter = dialect.getPrimitiveConverter( pm.getMapping().toString(), pm.getType() );
            } else {
                converter = instantiateConverter( pm.getConverter() );
                ( (CustomParticleConverter<TypedObjectNode>) converter ).init( particleMapping, this );
            }
            particeMappingToConverter.put( particleMapping, converter );
        } else if ( particleMapping instanceof GeometryMapping ) {
            GeometryMapping gm = (GeometryMapping) particleMapping;
            ParticleConverter<?> converter = getGeometryConverter( gm );
            particeMappingToConverter.put( particleMapping, converter );
        } else if ( particleMapping instanceof FeatureMapping ) {
            FeatureMapping fm = (FeatureMapping) particleMapping;
            String fkColumn = null;
            if ( fm.getJoinedTable() != null && !fm.getJoinedTable().isEmpty() ) {
                // TODO more complex joins
                fkColumn = fm.getJoinedTable().get( fm.getJoinedTable().size() - 1 ).getFromColumns().get( 0 );
            }
            String hrefColumn = null;
            if ( fm.getHrefMapping() != null ) {
                hrefColumn = fm.getHrefMapping().toString();
            }
            FeatureType valueFt = null;
            if ( fm.getValueFtName() != null ) {
                valueFt = schema.getFeatureType( fm.getValueFtName() );
            }
            ParticleConverter<?> converter = new FeatureParticleConverter( fkColumn, hrefColumn, getResolver(),
                                                                           valueFt, schema );
            particeMappingToConverter.put( particleMapping, converter );
        } else if ( particleMapping instanceof CompoundMapping ) {
            CompoundMapping cm = (CompoundMapping) particleMapping;
            for ( Mapping childMapping : cm.getParticles() ) {
                initConverter( childMapping );
            }
        } else {
            LOG.warn( "Unhandled particle mapping type {}", particleMapping );
        }
    }

    ParticleConverter<Geometry> getGeometryConverter( GeometryMapping geomMapping ) {
        String column = geomMapping.getMapping().toString();
        ICRS crs = geomMapping.getCRS();
        String srid = geomMapping.getSrid();
        boolean is2d = geomMapping.getDim() == CoordinateDimension.DIM_2;
        return dialect.getGeometryConverter( column, crs, srid, is2d );
    }

    @SuppressWarnings("unchecked")
    private CustomParticleConverter<TypedObjectNode> instantiateConverter( CustomConverterJAXB config ) {
        String className = config.getClazz();
        LOG.info( "Instantiating configured custom particle converter (class=" + className + ")" );
        try {
            return (CustomParticleConverter<TypedObjectNode>) workspace.getModuleClassLoader().loadClass( className ).newInstance();
        } catch ( Throwable t ) {
            String msg = "Unable to instantiate custom particle converter (class=" + className + "). "
                         + " Maybe directory 'modules' in your workspace is missing the JAR with the "
                         + " referenced converter class?! " + t.getMessage();
            LOG.error( msg, t );
            throw new IllegalArgumentException( msg );
        }
    }

    @Override
    public MappedApplicationSchema getSchema() {
        return schema;
    }

    public String getConnId() {
        return jdbcConnId;
    }

    /**
     * Returns the relational mapping for the given feature type name.
     * 
     * @param ftName
     *            name of the feature type, must not be <code>null</code>
     * @return relational mapping for the feature type, may be <code>null</code> (no relational mapping)
     */
    public FeatureTypeMapping getMapping( QName ftName ) {
        return schema.getFtMapping( ftName );
    }

    /**
     * Returns a {@link ParticleConverter} for the given {@link Mapping} instance from the served
     * {@link MappedApplicationSchema}.
     * 
     * @param mapping
     *            particle mapping, must not be <code>null</code>
     * @return particle converter, never <code>null</code>
     */
    public ParticleConverter<?> getConverter( Mapping mapping ) {
        return particeMappingToConverter.get( mapping );
    }

    @Override
    public Envelope getEnvelope( QName ftName )
                            throws FeatureStoreException {
        if ( !bboxCache.contains( ftName ) ) {
            calcEnvelope( ftName );
        }
        return bboxCache.get( ftName );
    }

    @Override
    public Envelope calcEnvelope( QName ftName )
                            throws FeatureStoreException {

        Envelope env = null;
        Connection conn = null;
        try {
            conn = ConnectionManager.getConnection( getConnId() );
            env = calcEnvelope( ftName, conn );
        } catch ( SQLException e ) {
            LOG.debug( e.getMessage(), e );
            throw new FeatureStoreException( e.getMessage(), e );
        } finally {
            JDBCUtils.close( conn );
        }
        return env;
    }

    Envelope calcEnvelope( QName ftName, Connection conn )
                            throws FeatureStoreException {
        Envelope env = null;
        FeatureType ft = schema.getFeatureType( ftName );
        if ( ft != null ) {
            // TODO what should be favored for hybrid mappings?
            if ( blobMapping != null ) {
                env = calcEnvelope( ftName, blobMapping, conn );
            } else if ( schema.getFtMapping( ft.getName() ) != null ) {
                FeatureTypeMapping ftMapping = schema.getFtMapping( ft.getName() );
                env = calcEnvelope( ftMapping, conn );
            }
        }
        bboxCache.set( ftName, env );
        return env;
    }

    private Envelope calcEnvelope( FeatureTypeMapping ftMapping, Connection conn )
                            throws FeatureStoreException {

        LOG.trace( "Determining BBOX for feature type '{}' (relational mode)", ftMapping.getFeatureType() );

        String column = null;
        FeatureType ft = getSchema().getFeatureType( ftMapping.getFeatureType() );
        GeometryPropertyType pt = ft.getDefaultGeometryPropertyDeclaration();
        if ( pt == null ) {
            return null;
        }
        Mapping propMapping = ftMapping.getMapping( pt.getName() );
        GeometryMapping mapping = Mappings.getGeometryMapping( propMapping );
        if ( mapping == null ) {
            return null;
        }
        MappingExpression me = mapping.getMapping();
        if ( me == null || !( me instanceof DBField ) ) {
            String msg = "Cannot determine BBOX for feature type '" + ft.getName() + "' (relational mode).";
            LOG.warn( msg );
            return null;
        }
        column = ( (DBField) me ).getColumn();

        Envelope env = null;
        StringBuilder sql = new StringBuilder( "SELECT " );
        sql.append( dialect.getBBoxAggregateSnippet( column ) );
        sql.append( " FROM " );
        sql.append( ftMapping.getFtTable() );

        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.createStatement();
            LOG.debug( "Executing envelope SELECT: " + sql );
            rs = stmt.executeQuery( sql.toString() );
            rs.next();
            ICRS crs = mapping.getCRS();
            env = dialect.getBBoxAggregateValue( rs, 1, crs );
        } catch ( SQLException e ) {
            LOG.debug( e.getMessage(), e );
            throw new FeatureStoreException( e.getMessage(), e );
        } finally {
            close( rs, stmt, null, LOG );
        }
        return env;
    }

    private Envelope calcEnvelope( QName ftName, BlobMapping blobMapping, Connection conn )
                            throws FeatureStoreException {

        LOG.debug( "Determining BBOX for feature type '{}' (BLOB mode)", ftName );

        int ftId = getFtId( ftName );
        String column = blobMapping.getBBoxColumn();

        Envelope env = null;
        StringBuilder sql = new StringBuilder( "SELECT " );
        sql.append( dialect.getBBoxAggregateSnippet( column ) );
        sql.append( " FROM " );
        sql.append( blobMapping.getTable() );
        sql.append( " WHERE " );
        sql.append( blobMapping.getTypeColumn() );
        sql.append( "=" );
        sql.append( ftId );

        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery( sql.toString() );
            rs.next();
            ICRS crs = blobMapping.getCRS();
            env = dialect.getBBoxAggregateValue( rs, 1, crs );
        } catch ( SQLException e ) {
            LOG.debug( e.getMessage(), e );
            throw new FeatureStoreException( e.getMessage(), e );
        } finally {
            close( rs, stmt, null, LOG );
        }
        return env;
    }

    BBoxCache getBBoxCache() {
        return bboxCache;
    }

    @Override
    public GMLObject getObjectById( String id )
                            throws FeatureStoreException {

        GMLObject geomOrFeature = getCache().get( id );
        if ( geomOrFeature == null ) {
            if ( getSchema().getBlobMapping() != null ) {
                geomOrFeature = getObjectByIdBlob( id, getSchema().getBlobMapping() );
            } else {
                geomOrFeature = getObjectByIdRelational( id );
            }
        }
        return geomOrFeature;
    }

    private GMLObject getObjectByIdBlob( String id, BlobMapping blobMapping )
                            throws FeatureStoreException {
        GMLObject geomOrFeature = null;
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            StringBuilder sql = new StringBuilder( "SELECT " );
            sql.append( blobMapping.getDataColumn() );
            sql.append( " FROM " );
            sql.append( blobMapping.getTable() );
            sql.append( " WHERE " );
            sql.append( blobMapping.getGMLIdColumn() );
            sql.append( "=?" );

            conn = ConnectionManager.getConnection( getConnId() );
            stmt = conn.prepareStatement( sql.toString() );
            stmt.setString( 1, id );
            rs = stmt.executeQuery();
            if ( rs.next() ) {
                LOG.debug( "Recreating object '" + id + "' from bytea." );
                BlobCodec codec = blobMapping.getCodec();
                geomOrFeature = codec.decode( rs.getBinaryStream( 1 ), getNamespaceContext(), getSchema(),
                                              blobMapping.getCRS(), new FeatureStoreGMLIdResolver( this ) );
                getCache().add( geomOrFeature );
            }
        } catch ( Exception e ) {
            String msg = "Error retrieving object by id (BLOB mode): " + e.getMessage();
            LOG.debug( msg, e );
            throw new FeatureStoreException( msg, e );
        } finally {
            close( rs, stmt, conn, LOG );
        }
        return geomOrFeature;
    }

    private GMLObject getObjectByIdRelational( String id )
                            throws FeatureStoreException {

        GMLObject result = null;

        IdAnalysis idAnalysis = getSchema().analyzeId( id );
        if ( !idAnalysis.isFid() ) {
            String msg = "Fetching of geometries by id (relational mode) is not implemented yet.";
            throw new UnsupportedOperationException( msg );
        }

        FeatureResultSet rs = queryByIdFilterRelational( new IdFilter( id ), null );
        try {
            Iterator<Feature> iter = rs.iterator();
            if ( iter.hasNext() ) {
                result = iter.next();
            }
        } finally {
            rs.close();
        }
        return result;
    }

    @Override
    public LockManager getLockManager()
                            throws FeatureStoreException {
        return lockManager;
    }

    @Override
    public FeatureStoreTransaction acquireTransaction()
                            throws FeatureStoreException {
        return taManager.acquireTransaction();
    }

    /**
     * Returns the {@link FeatureStoreCache}.
     * 
     * @return feature store cache, never <code>null</code>
     */
    public FeatureStoreCache getCache() {
        return cache;
    }

    /**
     * Returns a resolver instance for resolving references to objects that are stored in this feature store.
     * 
     * @return resolver, never <code>null</code>
     */
    public GMLReferenceResolver getResolver() {
        return resolver;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void destroy() {
        // nothing to do
    }

    @Override
    public int queryHits( Query query )
                            throws FeatureStoreException, FilterEvaluationException {
        // TODO
        return query( query ).toCollection().size();
    }

    @Override
    public int queryHits( final Query[] queries )
                            throws FeatureStoreException, FilterEvaluationException {
        // TODO
        return query( queries ).toCollection().size();
    }

    public Map<String, String> getNamespaceContext() {
        if ( nsContext == null ) {
            nsContext = new HashMap<String, String>( getSchema().getNamespaceBindings() );
            nsContext.put( "xlink", XLNNS );
            nsContext.put( "xsi", XSINS );
            nsContext.put( "ogc", OGCNS );
        }
        return nsContext;
    }

    /**
     * Returns a transformed version of the given {@link Geometry} in the specified CRS.
     * 
     * @param literal
     * @param crs
     * @return transformed version of the geometry, never <code>null</code>
     * @throws FilterEvaluationException
     */
    public static Geometry getCompatibleGeometry( Geometry literal, ICRS crs )
                            throws FilterEvaluationException {
        if ( crs == null ) {
            return literal;
        }

        Geometry transformedLiteral = literal;
        if ( literal != null ) {
            ICRS literalCRS = literal.getCoordinateSystem();
            if ( literalCRS != null && !( crs.equals( literalCRS ) ) ) {
                LOG.debug( "Need transformed literal geometry for evaluation: " + literalCRS.getAlias() + " -> "
                           + crs.getAlias() );
                try {
                    GeometryTransformer transformer = new GeometryTransformer( crs );
                    transformedLiteral = transformer.transform( literal );
                } catch ( Exception e ) {
                    throw new FilterEvaluationException( e.getMessage() );
                }
            }
        }
        return transformedLiteral;
    }

    short getFtId( QName ftName ) {
        return getSchema().getFtId( ftName );
    }

    @Override
    public FeatureResultSet query( Query query )
                            throws FeatureStoreException, FilterEvaluationException {

        if ( query.getTypeNames() == null || query.getTypeNames().length > 1 ) {
            String msg = "Join queries between multiple feature types are not supported yet.";
            throw new UnsupportedOperationException( msg );
        }

        FeatureResultSet result = null;
        Filter filter = query.getFilter();

        if ( query.getTypeNames().length == 1 && ( filter == null || filter instanceof OperatorFilter ) ) {
            QName ftName = query.getTypeNames()[0].getFeatureTypeName();
            FeatureType ft = getSchema().getFeatureType( ftName );
            if ( ft == null ) {
                String msg = "Feature type '" + ftName + "' is not served by this feature store.";
                throw new FeatureStoreException( msg );
            }
            result = queryByOperatorFilter( query, ftName, (OperatorFilter) filter );
        } else {
            // must be an id filter based query
            if ( query.getFilter() == null || !( query.getFilter() instanceof IdFilter ) ) {
                String msg = "Invalid query. If no type names are specified, it must contain an IdFilter.";
                throw new FilterEvaluationException( msg );
            }
            result = queryByIdFilter( (IdFilter) filter, query.getSortProperties() );
        }
        return result;
    }

    @Override
    public FeatureResultSet query( final Query[] queries )
                            throws FeatureStoreException, FilterEvaluationException {

        // check for most common case: multiple featuretypes, same bbox (WMS), no filter
        boolean wmsStyleQuery = false;
        Envelope env = queries[0].getPrefilterBBox();
        if ( getSchema().getBlobMapping() != null && queries[0].getFilter() == null
             && queries[0].getSortProperties().length == 0 ) {
            wmsStyleQuery = true;
            for ( int i = 1; i < queries.length; i++ ) {
                Envelope queryBBox = queries[i].getPrefilterBBox();
                if ( queryBBox != env && queries[i].getFilter() != null && queries[i].getSortProperties() != null ) {
                    wmsStyleQuery = false;
                    break;
                }
            }
        }

        if ( wmsStyleQuery ) {
            return queryMultipleFts( queries, env );
        }

        Iterator<FeatureResultSet> rsIter = new Iterator<FeatureResultSet>() {
            int i = 0;

            @Override
            public boolean hasNext() {
                return i < queries.length;
            }

            @Override
            public FeatureResultSet next() {
                if ( !hasNext() ) {
                    throw new NoSuchElementException();
                }
                FeatureResultSet rs;
                try {
                    rs = query( queries[i++] );
                } catch ( Throwable e ) {
                    LOG.debug( e.getMessage(), e );
                    throw new RuntimeException( e.getMessage(), e );
                }
                return rs;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
        return new CombinedResultSet( rsIter );
    }

    private FeatureResultSet queryByIdFilter( IdFilter filter, SortProperty[] sortCrit )
                            throws FeatureStoreException {
        if ( blobMapping != null ) {
            return queryByIdFilterBlob( filter, sortCrit );
        }
        return queryByIdFilterRelational( filter, sortCrit );
    }

    private FeatureResultSet queryByIdFilterBlob( IdFilter filter, SortProperty[] sortCrit )
                            throws FeatureStoreException {

        FeatureResultSet result = null;
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            conn = getConnection();

            StringBuilder sb = new StringBuilder( filter.getMatchingIds().size() * 2 );
            sb.append( "?" );
            for ( int i = 1; i < filter.getMatchingIds().size(); ++i ) {
                sb.append( ",?" );
            }
            stmt = conn.prepareStatement( "SELECT gml_id,binary_object FROM " + blobMapping.getTable()
                                          + " A WHERE A.gml_id in (" + sb + ")" );
            int idx = 0;
            for ( String id : filter.getMatchingIds() ) {
                stmt.setString( ++idx, id );
            }
            rs = stmt.executeQuery();

            FeatureBuilder builder = new FeatureBuilderBlob( this, blobMapping );
            result = new IteratorResultSet( new FeatureResultSetIterator( builder, rs, conn, stmt ) );
        } catch ( Exception e ) {
            close( rs, stmt, conn, LOG );
            String msg = "Error performing id query: " + e.getMessage();
            LOG.debug( msg, e );
            throw new FeatureStoreException( msg, e );
        }

        // sort features
        if ( sortCrit.length > 0 ) {
            result = new MemoryFeatureResultSet( Features.sortFc( result.toCollection(), sortCrit ) );
        }
        return result;
    }

    private FeatureResultSet queryByIdFilterRelational( IdFilter filter, SortProperty[] sortCrit )
                            throws FeatureStoreException {

        LinkedHashMap<QName, List<IdAnalysis>> ftNameToIdAnalysis = new LinkedHashMap<QName, List<IdAnalysis>>();
        try {
            for ( String fid : filter.getMatchingIds() ) {
                IdAnalysis analysis = getSchema().analyzeId( fid );
                FeatureType ft = analysis.getFeatureType();
                List<IdAnalysis> idKernels = ftNameToIdAnalysis.get( ft.getName() );
                if ( idKernels == null ) {
                    idKernels = new ArrayList<IdAnalysis>();
                    ftNameToIdAnalysis.put( ft.getName(), idKernels );
                }
                idKernels.add( analysis );
            }
        } catch ( IllegalArgumentException e ) {
            throw new FeatureStoreException( e.getMessage(), e );
        }

        if ( ftNameToIdAnalysis.size() != 1 ) {
            throw new FeatureStoreException(
                                             "Currently, only relational id queries are supported that target single feature types." );
        }

        QName ftName = ftNameToIdAnalysis.keySet().iterator().next();
        FeatureType ft = getSchema().getFeatureType( ftName );
        FeatureTypeMapping ftMapping = getSchema().getFtMapping( ftName );
        FIDMapping fidMapping = ftMapping.getFidMapping();
        List<IdAnalysis> idKernels = ftNameToIdAnalysis.get( ftName );

        FeatureResultSet result = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        Connection conn = null;
        try {
            long begin = System.currentTimeMillis();
            conn = getConnection();

            String tableAlias = "X1";
            FeatureBuilder builder = new FeatureBuilderRelational( this, ft, ftMapping, conn, tableAlias );
            List<String> columns = builder.getInitialSelectColumns();
            StringBuilder sql = new StringBuilder( "SELECT " );
            sql.append( columns.get( 0 ) );
            for ( int i = 1; i < columns.size(); i++ ) {
                sql.append( ',' );
                sql.append( columns.get( i ) );
            }
            sql.append( " FROM " );
            sql.append( ftMapping.getFtTable() );
            sql.append( ' ' );
            sql.append( tableAlias );
            sql.append( " WHERE " );
            boolean first = true;
            for ( IdAnalysis idKernel : idKernels ) {
                if ( !first ) {
                    sql.append( " OR " );
                }
                sql.append( "(" );
                boolean firstCol = true;
                for ( Pair<String, BaseType> fidColumn : fidMapping.getColumns() ) {
                    if ( !firstCol ) {
                        sql.append( " AND " );
                    }
                    sql.append( fidColumn.first );
                    sql.append( "=?" );
                    firstCol = false;
                }
                sql.append( ")" );
                first = false;
            }
            LOG.debug( "SQL: {}", sql );

            stmt = conn.prepareStatement( sql.toString() );
            LOG.debug( "Preparing SELECT took {} [ms] ", System.currentTimeMillis() - begin );

            int i = 1;
            for ( IdAnalysis idKernel : idKernels ) {
                for ( Object o : idKernel.getIdKernels() ) {
                    PrimitiveType pt = new PrimitiveType( fidMapping.getColumns().get( i - 1 ).getSecond() );
                    PrimitiveValue value = new PrimitiveValue( o, pt );
                    Object sqlValue = SQLValueMangler.internalToSQL( value );
                    stmt.setObject( i++, sqlValue );
                }
            }

            begin = System.currentTimeMillis();
            rs = stmt.executeQuery();
            LOG.debug( "Executing SELECT took {} [ms] ", System.currentTimeMillis() - begin );
            result = new IteratorResultSet( new FeatureResultSetIterator( builder, rs, conn, stmt ) );
        } catch ( Exception e ) {
            close( rs, stmt, conn, LOG );
            String msg = "Error performing query by id filter (relational mode): " + e.getMessage();
            LOG.error( msg, e );
            throw new FeatureStoreException( msg, e );
        }
        return result;
    }

    protected Connection getConnection()
                            throws SQLException {
        Connection conn = ConnectionManager.getConnection( getConnId() );
        // TODO where to put this?
        conn.setAutoCommit( false );
        return conn;
    }

    private FeatureResultSet queryByOperatorFilterBlob( Query query, QName ftName, OperatorFilter filter )
                            throws FeatureStoreException {
        LOG.debug( "Performing blob query by operator filter" );

        AbstractWhereBuilder wb = null;
        Connection conn = null;
        FeatureResultSet result = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = getConnection();

            FeatureTypeMapping ftMapping = getMapping( ftName );
            BlobMapping blobMapping = getSchema().getBlobMapping();
            FeatureBuilder builder = new FeatureBuilderBlob( this, blobMapping );

            List<String> columns = builder.getInitialSelectColumns();

            if ( query.getPrefilterBBox() != null ) {
                OperatorFilter bboxFilter = new OperatorFilter( new BBOX( query.getPrefilterBBox() ) );
                wb = getWhereBuilderBlob( bboxFilter, conn );
                LOG.debug( "WHERE clause: " + wb.getWhere() );
                // LOG.debug( "ORDER BY clause: " + wb.getOrderBy() );
            }
            String alias = wb != null ? wb.getAliasManager().getRootTableAlias() : "X1";

            StringBuilder sql = new StringBuilder( "SELECT " );
            sql.append( columns.get( 0 ) );
            for ( int i = 1; i < columns.size(); i++ ) {
                sql.append( ',' );
                sql.append( columns.get( i ) );
            }
            sql.append( " FROM " );
            if ( ftMapping == null ) {
                // pure BLOB query
                sql.append( blobMapping.getTable() );
                sql.append( ' ' );
                sql.append( alias );
                // } else {
                // hybrid query
                // sql.append( blobMapping.getTable() );
                // sql.append( ' ' );
                // sql.append( alias );
                // sql.append( " LEFT OUTER JOIN " );
                // sql.append( ftMapping.getFtTable() );
                // sql.append( ' ' );
                // sql.append( alias );
                // sql.append( " ON " );
                // sql.append( alias );
                // sql.append( "." );
                // sql.append( blobMapping.getInternalIdColumn() );
                // sql.append( "=" );
                // sql.append( alias );
                // sql.append( "." );
                // sql.append( ftMapping.getFidMapping().getColumn() );
            }

            if ( wb != null ) {
                for ( PropertyNameMapping mappedPropName : wb.getMappedPropertyNames() ) {
                    for ( Join join : mappedPropName.getJoins() ) {
                        sql.append( " LEFT OUTER JOIN " );
                        sql.append( join.getToTable() );
                        sql.append( ' ' );
                        sql.append( join.getToTableAlias() );
                        sql.append( " ON " );
                        sql.append( join.getSQLJoinCondition() );
                    }
                }
            }
            sql.append( " WHERE " );
            sql.append( alias );
            sql.append( "." );
            sql.append( blobMapping.getTypeColumn() );
            sql.append( "=?" );
            if ( wb != null ) {
                sql.append( " AND " );
                sql.append( wb.getWhere().getSQL() );
            }

            // if ( wb != null && wb.getWhere() != null ) {
            // if ( blobMapping != null ) {
            // sql.append( " AND " );
            // } else {
            // sql.append( " WHERE " );
            // }
            // sql.append( wb.getWhere().getSQL() );
            // }
            // if ( wb != null && wb.getOrderBy() != null ) {
            // sql.append( " ORDER BY " );
            // sql.append( wb.getOrderBy().getSQL() );
            // }

            LOG.debug( "SQL: {}", sql );
            long begin = System.currentTimeMillis();
            stmt = conn.prepareStatement( sql.toString() );
            LOG.debug( "Preparing SELECT took {} [ms] ", System.currentTimeMillis() - begin );

            int i = 1;
            // if ( blobMapping != null ) {
            stmt.setShort( i++, getSchema().getFtId( ftName ) );
            if ( wb != null ) {
                for ( SQLArgument o : wb.getWhere().getArguments() ) {
                    o.setArgument( stmt, i++ );
                }
            }
            // }
            // if ( wb != null && wb.getWhere() != null ) {
            // for ( SQLArgument o : wb.getWhere().getArguments() ) {
            // o.setArgument( stmt, i++ );
            // }
            // }
            // if ( wb != null && wb.getOrderBy() != null ) {
            // for ( SQLArgument o : wb.getOrderBy().getArguments() ) {
            // o.setArgument( stmt, i++ );
            // }
            // }

            begin = System.currentTimeMillis();

            // TODO make this configurable?
            stmt.setFetchSize( 1 );
            rs = stmt.executeQuery();
            LOG.debug( "Executing SELECT took {} [ms] ", System.currentTimeMillis() - begin );

            result = new IteratorResultSet( new FeatureResultSetIterator( builder, rs, conn, stmt ) );
        } catch ( Exception e ) {
            close( rs, stmt, conn, LOG );
            String msg = "Error performing query by operator filter: " + e.getMessage();
            LOG.error( msg, e );
            throw new FeatureStoreException( msg, e );
        }

        if ( filter != null ) {
            LOG.debug( "Applying in-memory post-filtering." );
            result = new FilteredFeatureResultSet( result, filter );
        }

        if ( query.getSortProperties().length > 0 ) {
            LOG.debug( "Applying in-memory post-sorting." );
            result = new MemoryFeatureResultSet( Features.sortFc( result.toCollection(), query.getSortProperties() ) );
        }
        return result;
    }

    private FeatureResultSet queryByOperatorFilter( Query query, QName ftName, OperatorFilter filter )
                            throws FeatureStoreException {

        LOG.debug( "Performing query by operator filter" );

        if ( getSchema().getBlobMapping() != null ) {
            return queryByOperatorFilterBlob( query, ftName, filter );
        }

        AbstractWhereBuilder wb = null;
        Connection conn = null;
        FeatureResultSet result = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        FeatureType ft = getSchema().getFeatureType( ftName );
        FeatureTypeMapping ftMapping = getMapping( ftName );
        if ( ftMapping == null ) {
            String msg = "Cannot perform query on feature type '" + ftName + "'. Feature type is not mapped.";
            throw new FeatureStoreException( msg );
        }

        try {
            conn = getConnection();

            wb = getWhereBuilder( ft, filter, query.getSortProperties(), conn );
            String ftTableAlias = wb.getAliasManager().getRootTableAlias();
            LOG.debug( "WHERE clause: " + wb.getWhere() );
            LOG.debug( "ORDER BY clause: " + wb.getOrderBy() );

            FeatureBuilder builder = new FeatureBuilderRelational( this, ft, ftMapping, conn, ftTableAlias );
            List<String> columns = builder.getInitialSelectColumns();

            BlobMapping blobMapping = getSchema().getBlobMapping();

            StringBuilder sql = new StringBuilder( "SELECT " );
            sql.append( columns.get( 0 ) );
            for ( int i = 1; i < columns.size(); i++ ) {
                sql.append( ',' );
                sql.append( columns.get( i ) );
            }
            sql.append( " FROM " );

            // pure relational query
            sql.append( ftMapping.getFtTable() );
            sql.append( ' ' );
            sql.append( ftTableAlias );

            for ( PropertyNameMapping mappedPropName : wb.getMappedPropertyNames() ) {
                for ( Join join : mappedPropName.getJoins() ) {
                    sql.append( " LEFT OUTER JOIN " );
                    sql.append( join.getToTable() );
                    sql.append( ' ' );
                    sql.append( join.getToTableAlias() );
                    sql.append( " ON " );
                    sql.append( join.getSQLJoinCondition() );
                }
            }

            if ( wb.getWhere() != null ) {
                if ( blobMapping != null ) {
                    sql.append( " AND " );
                } else {
                    sql.append( " WHERE " );
                }
                sql.append( wb.getWhere().getSQL() );
            }
            if ( wb.getOrderBy() != null ) {
                sql.append( " ORDER BY " );
                sql.append( wb.getOrderBy().getSQL() );
            }

            LOG.debug( "SQL: {}", sql );
            long begin = System.currentTimeMillis();
            stmt = conn.prepareStatement( sql.toString() );
            LOG.debug( "Preparing SELECT took {} [ms] ", System.currentTimeMillis() - begin );

            int i = 1;
            if ( wb.getWhere() != null ) {
                for ( SQLArgument o : wb.getWhere().getArguments() ) {
                    o.setArgument( stmt, i++ );
                }
            }
            if ( wb.getOrderBy() != null ) {
                for ( SQLArgument o : wb.getOrderBy().getArguments() ) {
                    o.setArgument( stmt, i++ );
                }
            }

            begin = System.currentTimeMillis();

            // TODO make this configurable?
            stmt.setFetchSize( 1 );
            rs = stmt.executeQuery();
            LOG.debug( "Executing SELECT took {} [ms] ", System.currentTimeMillis() - begin );

            result = new IteratorResultSet( new FeatureResultSetIterator( builder, rs, conn, stmt ) );
        } catch ( Exception e ) {
            close( rs, stmt, conn, LOG );
            String msg = "Error performing query by operator filter: " + e.getMessage();
            LOG.error( msg, e );
            throw new FeatureStoreException( msg, e );
        }

        if ( wb.getPostFilter() != null ) {
            LOG.debug( "Applying in-memory post-filtering." );
            result = new FilteredFeatureResultSet( result, wb.getPostFilter() );
        }
        if ( wb.getPostSortCriteria() != null ) {
            LOG.debug( "Applying in-memory post-sorting." );
            result = new MemoryFeatureResultSet( Features.sortFc( result.toCollection(), wb.getPostSortCriteria() ) );
        }
        return result;
    }

    private FeatureResultSet queryMultipleFts( Query[] queries, Envelope looseBBox )
                            throws FeatureStoreException {

        FeatureResultSet result = null;

        short[] ftId = new short[queries.length];
        for ( int i = 0; i < ftId.length; i++ ) {
            Query query = queries[i];
            if ( query.getTypeNames() == null || query.getTypeNames().length > 1 ) {
                String msg = "Join queries between multiple feature types are currently not supported.";
                throw new UnsupportedOperationException( msg );
            }
            ftId[i] = getFtId( query.getTypeNames()[0].getFeatureTypeName() );
        }

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        AbstractWhereBuilder blobWb = null;
        try {
            if ( looseBBox != null ) {
                OperatorFilter bboxFilter = new OperatorFilter( new BBOX( looseBBox ) );
                blobWb = getWhereBuilderBlob( bboxFilter, conn );
            }

            conn = getConnection();
            StringBuffer sql = new StringBuffer( "SELECT gml_id,binary_object FROM " + blobMapping.getTable()
                                                 + " WHERE " );
            if ( looseBBox != null ) {
                sql.append( "gml_bounded_by && ? AND " );
            }
            sql.append( "ft_type IN(?" );
            for ( int i = 1; i < ftId.length; i++ ) {
                sql.append( ",?" );
            }
            sql.append( ") ORDER BY " );
            sql.append( dialect.stringIndex( "'['" + dialect.stringPlus() + dialect.cast( "ft_type", "varchar(2000)" )
                                             + dialect.stringPlus() + "']'", "?" ) );
            stmt = conn.prepareStatement( sql.toString() );
            int firstFtArg = 1;
            if ( blobWb != null && blobWb.getWhere() != null ) {
                for ( SQLArgument o : blobWb.getWhere().getArguments() ) {
                    o.setArgument( stmt, firstFtArg++ );
                }
            }
            StringBuffer orderString = new StringBuffer();
            for ( int i = 0; i < ftId.length; i++ ) {
                stmt.setShort( i + firstFtArg, ftId[i] );
                orderString.append( "[" );
                orderString.append( "" + ftId[i] );
                orderString.append( "]" );
            }
            stmt.setString( ftId.length + firstFtArg, orderString.toString() );
            LOG.debug( "Query: {}", sql );
            LOG.debug( "Prepared: {}", stmt );

            rs = stmt.executeQuery();
            FeatureBuilder builder = new FeatureBuilderBlob( this, blobMapping );
            result = new IteratorResultSet( new FeatureResultSetIterator( builder, rs, conn, stmt ) );
        } catch ( Exception e ) {
            close( rs, stmt, conn, LOG );
            String msg = "Error performing query: " + e.getMessage();
            LOG.debug( msg );
            LOG.trace( "Stack trace:", e );
            throw new FeatureStoreException( msg, e );
        }
        return result;
    }

    private AbstractWhereBuilder getWhereBuilder( FeatureType ft, OperatorFilter filter, SortProperty[] sortCrit,
                                                  Connection conn )
                            throws FilterEvaluationException, UnmappableException {
        PropertyNameMapper mapper = new SQLPropertyNameMapper( this, getMapping( ft.getName() ) );
        return dialect.getWhereBuilder( mapper, filter, sortCrit, allowInMemoryFiltering );
    }

    private AbstractWhereBuilder getWhereBuilderBlob( OperatorFilter filter, Connection conn )
                            throws FilterEvaluationException, UnmappableException {
        final String undefinedSrid = dialect.getUndefinedSrid();
        PropertyNameMapper mapper = new PropertyNameMapper() {
            @Override
            public PropertyNameMapping getMapping( PropertyName propName, TableAliasManager aliasManager )
                                    throws FilterEvaluationException, UnmappableException {
                GeometryStorageParams geometryParams = new GeometryStorageParams( blobMapping.getCRS(), undefinedSrid,
                                                                                  CoordinateDimension.DIM_2 );
                GeometryMapping bboxMapping = new GeometryMapping( null, false,
                                                                   new DBField( blobMapping.getBBoxColumn() ),
                                                                   GeometryType.GEOMETRY, geometryParams, null );
                return new PropertyNameMapping( getGeometryConverter( bboxMapping ), null, blobMapping.getBBoxColumn(),
                                                aliasManager.getRootTableAlias() );
            }
        };
        return dialect.getWhereBuilder( mapper, filter, null, allowInMemoryFiltering );
    }

    public SQLDialect getDialect() {
        return dialect;
    }

    private class FeatureResultSetIterator extends ResultSetIterator<Feature> {

        private final FeatureBuilder builder;

        public FeatureResultSetIterator( FeatureBuilder builder, ResultSet rs, Connection conn, Statement stmt ) {
            super( rs, conn, stmt );
            this.builder = builder;
        }

        @Override
        protected Feature createElement( ResultSet rs )
                                throws SQLException {
            return builder.buildFeature( rs );
        }
    }
}