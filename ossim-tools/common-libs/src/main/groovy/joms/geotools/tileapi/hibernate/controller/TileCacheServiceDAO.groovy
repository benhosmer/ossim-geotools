package joms.geotools.tileapi.hibernate.controller

import com.vividsolutions.jts.geom.Geometry
import com.vividsolutions.jts.io.WKTReader
import geoscript.geom.Bounds
import geoscript.geom.io.KmlReader
import geoscript.layer.Pyramid
import geoscript.proj.Projection
import groovy.sql.Sql
import joms.geotools.tileapi.BoundsUtil
import joms.geotools.tileapi.OssimImageTileRenderer
import joms.geotools.tileapi.accumulo.AccumuloApi
import joms.geotools.tileapi.TileCachePyramid
import joms.geotools.tileapi.accumulo.AccumuloTileLayer
import joms.geotools.tileapi.accumulo.ImageTileKey
import joms.geotools.tileapi.accumulo.TileCacheImageTile
import joms.geotools.tileapi.DateUtil
import joms.geotools.tileapi.TileCacheTileGenerator
import joms.geotools.tileapi.hibernate.domain.TileCacheLayerInfo
import joms.geotools.tileapi.hibernate.domain.TileCacheTileTableTemplate
import joms.oms.TileCacheSupport
import org.hibernate.SessionFactory
import org.springframework.beans.BeansException
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.transaction.annotation.Transactional

import javax.annotation.Resource


/**
 * Created by gpotts on 1/22/15.
 */
//class TileCacheServiceDAO extends DAOImpl<TileCacheTileTableTemplate> implements DAO<TileCacheTileTableTemplate>,
class TileCacheServiceDAO implements InitializingBean, DisposableBean, ApplicationContextAware
{

  @Resource(name = "sessionFactory")
  @Autowired
  SessionFactory sessionFactory
  AccumuloApi accumuloApi
  ApplicationContext applicationContext
  TileCacheLayerInfoDAO layerInfoTableDAO
  Sql sql
  def sqlSession


  void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    this.applicationContext = applicationContext;
  }
  void afterPropertiesSet()
  {
    layerInfoTableDAO = applicationContext?.getBean("tileCacheLayerInfoDAO")
    accumuloApi = applicationContext?.getBean("accumuloApi")
    sqlSession = sessionFactory.openSession()
    sql = new Sql(sqlSession.connection())
  }
  void destroy()
  {
    sql?.close()
    sqlSession?.close()
  }

  boolean tableExists(String table)
  {
    def result

    result = sql.connection.metaData.getTables(null, null, table,null)


    result?.first()
  }

  AccumuloTileLayer newGeoscriptTileLayer(TileCacheLayerInfo layerInfo)
  {
    AccumuloTileLayer result
    if(layerInfo)
    {
     // Envelope env = layerInfo.bounds.envelopeInternal
      Projection proj = new Projection(layerInfo.epsgCode)
      Bounds b          = BoundsUtil.getDefaultBounds(proj) //(layerInfo.epsgCode.toLowerCase() == 'epsg:3857') ? new Bounds(-20037508.342789244, -20037508.342789244, 20037508.342789244, 20037508.342789244, 'epsg:3857') : proj.bounds
      Bounds clipBounds = new Bounds(layerInfo.bounds.envelopeInternal)
      //new Bounds(env.minX, env.minY, env.maxX, env.maxY)
   //   if(layerInfo.epsgCode.toLowerCase().trim() == "epsg:4326")
   //   {
      def pyramid = new TileCachePyramid(bounds:b,
                                        clippedBounds: clipBounds,
                                        proj:proj,
                                        origin: Pyramid.Origin.TOP_LEFT,
                                        tileWidth: layerInfo.tileWidth,
                                        tileHeight: layerInfo.tileHeight
                                        )
      pyramid.initializeGrids(layerInfo.minLevel, layerInfo.maxLevel)
      result = new AccumuloTileLayer(tileCacheService:this,
              layerInfo:layerInfo,
              bounds:b,
              proj:proj,
              name:layerInfo.name,
              pyramid:pyramid)
    //  }
    }

    result
  }
  AccumuloTileLayer newGeoscriptTileLayer(String layerName)
  {
    newGeoscriptTileLayer(getLayerInfoByName(layerName))
  }
  TileCacheTileGenerator[] getTileGenerators(TileCacheLayerInfo layer, String input, def clipOptions=[:])
  {

    TileCacheSupport tileCacheSupport = new TileCacheSupport()

    def result = []

    //println "TRYING TO OPEN IMAGE ${input}"
    //println "LAYER ==== ${layer}"
    try{
      if(layer&&tileCacheSupport.openImage(input))
      {
        int entry = 0
        int numberOfEntries = tileCacheSupport.getNumberOfEntries()

        //println "N ENTRIES ==== ${numberOfEntries}"
        //println resolutions
        for(entry=0;entry<numberOfEntries;++entry)
        {

          int[] minLevel = [0] as int[]
          int[] maxLevel = [0] as int[]
          boolean needToStretch = tileCacheSupport.getBitDepth(entry) > 8
          //println "NEED TO STRETCH============ ${needToStretch}"
          int numberOfResolutionLevels = tileCacheSupport.getNumberOfResolutionLevels(entry)
          double gsd = tileCacheSupport.getDegreesPerPixel(entry)
          joms.oms.Envelope envelope = tileCacheSupport.getEnvelope(entry)
          Bounds bounds = new Bounds(envelope.minX, envelope.minY, envelope.maxX, envelope.maxY)

          AccumuloTileLayer tileLayer = newGeoscriptTileLayer(layer)
          double[] resolutions = tileLayer.pyramid.grids*.yResolution as double[]

          def intersections = tileLayer.pyramid.findIntersections(tileCacheSupport, entry, clipOptions)

          if(intersections)
          {
            // for the poly cutter in ossim it is a list of tuples
            // of the form ((<lat>,<lon>),......(<lat>,<lon>))
            def clipPolyLatLonKeyWord = intersections.clippedGeometryLatLon.points.collect{"(${it.y},${it.x})"}.join(",")
            OssimImageTileRenderer tileRenderer = new OssimImageTileRenderer(input, entry,
                    [cut_width:layer.tileWidth.toString(),
                     cut_height:layer.tileHeight.toString(),
                     hist_op:needToStretch?"auto-minmax":"none",
                     clip_poly_lat_lon:"(${clipPolyLatLonKeyWord})".toString()])
            TileCacheTileGenerator generator = new TileCacheTileGenerator(verbose:true,
                    tileLayer:tileLayer,
                    tileRenderer:tileRenderer,
                    startZoom:intersections.minLevel,
                    endZoom:intersections.maxLevel,
                    bounds:intersections.clippedBounds)//bounds)
            result << generator
          }
        }
      }
    }
    catch(def e)
    {
      e.printStackTrace()
    }

    tileCacheSupport.delete()
    tileCacheSupport = null
    result as TileCacheTileGenerator[]
  }

  TileCacheTileGenerator[] getTileGenerators(String layer, String input, def clipOptions=[:])
  {
    getTileGenerators(getLayerInfoByName(layer), input, clipOptions)
  }

  @Transactional
  private void createTileStore(String target)
  {
   // def tempSession = sessionFactory.openSession()
   // Sql sql = new Sql(tempSession.connection())
     //create_table_if_not_exists
    //CREATE TABLE ${target} as select * from tile_cache_tile_table_template with no data;
    String createStmt =  "CREATE TABLE ${target} as select * from tile_cache_tile_table_template with no data;"
//
    String sqlString = """
        SELECT create_table_if_not_exists('${target}','${createStmt}');
        SELECT create_index_if_not_exists('${target}', 'bounds_idx', 'USING GIST(bounds)');
        SELECT create_index_if_not_exists('${target}', 'hash_id_idx', '(hash_id)');
        SELECT create_index_if_not_exists('${target}', 'res_idx', '(res)');
    """.toString()
    sql.execute(sqlString)
  }

  @Transactional void renameLayer(String oldName, String newName) throws Exception
  {
    TileCacheLayerInfo layer = layerInfoTableDAO.findByName(oldName)
    if(layer)
    {
      def oldTileStore = layer.tileStoreTable
      String defaultTileStore = "omar_tilecache_${newName.toLowerCase()}_tiles"
      layer.tileStoreTable = defaultTileStore
      layer.name = newName
      layerInfoTableDAO.update(layer)
      sql.execute("ALTER TABLE ${oldTileStore} RENAME TO ${defaultTileStore}".toString());
      accumuloApi.renameTable(oldTileStore, defaultTileStore)
    }
    else
    {
      throw new Exception("Layer ${oldName} not found")
    }
  }

  @Transactional
  TileCacheLayerInfo createOrUpdateLayer(TileCacheLayerInfo layerInfo)
  {
    String defaultTileStore = "omar_tilecache_${layerInfo.name.toLowerCase()}_tiles"
    TileCacheLayerInfo layer = layerInfoTableDAO.findByName(layerInfo.name)

   //println "TABLE ${defaultTileStore} Exists???? ${tableExists(defaultTileStore)}"
    if(layer)
    {
      layer.copyNonNullValues(layerInfo)

      // rename not supported here
      if(layerInfo.name == layer.name)
      {
        layerInfoTableDAO.update(layer)
      }
      if(layer.tileStoreTable&&!tableExists(layer.tileStoreTable))
      {
        createTileStore(layer.tileStoreTable)
      }
    }
    else
    {
      if(!layerInfo.tileStoreTable)
      {
        layerInfo.tileStoreTable = defaultTileStore
      }

      layerInfoTableDAO.save(layerInfo)
      if(!tableExists(layerInfo.tileStoreTable))
      {
        createTileStore(layerInfo.tileStoreTable)
      }
      accumuloApi.createTable(layerInfo.tileStoreTable)
      layer = layerInfo
    }
    layer
  }

  @Transactional
  TileCacheLayerInfo createOrUpdateLayer(String name,
              Bounds bounds = new Projection("EPSG:4326").bounds,
              String epsgCode="EPSG:4326",
              int tileWidth=256,
              int tileHeight=256,
              int minLevel=0,
              int maxLevel=24)
  {
    createOrUpdateLayer(new TileCacheLayerInfo(name:name,
            bounds:bounds.polygon.g,
            epsgCode: epsgCode?:"EPSG:${bounds.proj.epsg}",
            tileHeight:tileHeight,
            tileWidth:tileWidth,
            minLevel:minLevel,
            maxLevel:maxLevel)
    )
  }

  @Transactional
  void deleteLayer(String name)
  {
    TileCacheLayerInfo layer = layerInfoTableDAO.findByName(name)
    if(layer)
    {
      if(tableExists(layer.tileStoreTable))
      {
        sql.execute("DROP TABLE ${layer.tileStoreTable};".toString())
      }
      accumuloApi.deleteTable(layer.tileStoreTable)
      layerInfoTableDAO.delete(layer)
    }
  }

  @Transactional
  TileCacheLayerInfo getLayerInfoByName(String name)
  {
    layerInfoTableDAO.findByName(name)
  }

  @Transactional
  List listAllLayers()
  {
    layerInfoTableDAO.list()
  }

  private String createWhereClause(def constraints)
  {
    def result = ""
    if(constraints)
    {
      def conjunction = " AND "

      def whereClause = ""

      if(constraints.intersects)
      {
        if(whereClause) whereClause += conjunction
        whereClause += "(ST_Intersects(ST_GeometryFromText('${constraints.intersects}'),bounds))"
      }
      if(constraints.afterDate&&constraints.beforeDate)
      {
        if(whereClause) whereClause += conjunction
        whereClause += "(modified_date BETWEEN '${DateUtil.formatTimezone(constraints.afterDate)}' AND '${DateUtil.formatTimezone(constraints.beforeDate)}')"

      }
      else if(constraints.afterDate)
      {
        if(whereClause) whereClause += conjunction
        whereClause += "(modified_date > '${DateUtil.formatTimezone(constraints.afterDate)}')"
      }
      else if(constraints.beforeDate)
      {
        if(whereClause) whereClause += conjunction
        whereClause += "(modified_date < '${DateUtil.formatTimezone(constraints.beforeDate)}')"
      }
      if(constraints.z!=null)
      {
        if(whereClause) whereClause += conjunction
        whereClause += "(z = ${constraints.z})"
      }
      if(constraints.x!=null)
      {
        if(whereClause) whereClause += conjunction
        whereClause += "(x = ${constraints.x})"
      }
      if(constraints.y!=null)
      {
        if(whereClause) whereClause += conjunction
        whereClause += "(y = ${constraints.y})"
      }
      result = whereClause?"where ${whereClause}":whereClause
    }
   // println "WHERE CLAUSE = ${result}"
    result
  }
  /**
   * constraints is a hash map that has values of either
   * afterDate: Date
   * beforeDate: Date
   * betweenDate:[Date, Date]
   * z:
   * x:
   * y:
   * If you want polygon intersects
   * intersect:<jts Polygon object>
   *
   *
   *
   * @param table
   * @param constraints can
   */
  @Transactional
  def getHashIdsWithinConstraint(TileCacheLayerInfo layer, HashMap constraints)
  {
    def result = []
    def queryString = "select hash_id from ${layer.tileStoreTable} ${createWhereClause(constraints)}".toString()

    if(constraints.offset&&constraints.maxRows)
    {
      sql.eachRow(queryString, constraints.offset, constraints.maxRows){row->
        result << row.hash_id
      }
    }
    else
    {
      sql.eachRow(queryString){row->
        result << row.hash_id
      }
    }
    result
  }
  @Transactional
  def getTilesWithinConstraint(TileCacheLayerInfo layer, HashMap constraints=[:])
  {
    def result = []
    def queryString = "select * from ${layer?.tileStoreTable} ${createWhereClause(constraints)}".toString()
    def metaRows = [:]
    def hashIds = []

    if(!layer) return result
    if(constraints.offset&&constraints.maxRows)
    {
      sql.eachRow(queryString, constraints.offset, constraints.maxRows){row->
        metaRows."${row.hash_id}" = new TileCacheTileTableTemplate().bindSql(row)
        hashIds << new ImageTileKey(rowId:row.hash_id)
      }
    }
    else
    {
      sql.eachRow(queryString){row->
        metaRows."${row.hash_id}" = new TileCacheTileTableTemplate().bindSql(row)
        hashIds << new ImageTileKey(rowId:row.hash_id)
      }
    }
    if(hashIds)
    {

      def tiles        = accumuloApi.getTiles(layer.tileStoreTable, hashIds as ImageTileKey[])
      tiles.each{k,v->

        TileCacheImageTile tile = v as  TileCacheImageTile
        def row = metaRows."${k}"
        if(row)
        {
          tile.x      = row.x
          tile.y      = row.y
          tile.z      = row.z
          tile.res    = row.res
          if(metaRows.modified_date) tile.modifiedDate = row.modifiedDate
          tile.bounds = new Bounds(row.bounds.envelopeInternal)//new WKTReader().read(meta.bounds.toString())

          result << tile
        }
      }

    }
    result
  }

  @Transactional
  def getActualLayerBounds(TileCacheLayerInfo info, HashMap constraints=[:])
  {
    def result = [minx:0.0,
                  miny:0.0,
                  maxx:0.0,
                  maxy:0.0,
                  minLevel:-1,
                  maxLevel:-1]
    if(info)
    {
      String queryString = "select ST_xmin(ST_Extent(bounds)) as minx, ST_ymin(ST_Extent(bounds)) as miny, ST_xmax(ST_Extent(bounds)) as maxx, ST_ymax(ST_Extent(bounds)) as maxy, min(z) as min_level, max(z) as max_level from ${info.tileStoreTable} ${createWhereClause(constraints)}".toString()
      sql.eachRow(queryString){row->
        if(row.minx!=null)
        {
          result.minx = row.minx
          result.miny = row.miny
          result.maxx = row.maxx
          result.maxy = row.maxy
          result.minLevel = row.min_level
          result.maxLevel = row.max_level
        }
      }
    }

    result
  }

  @Transactional
  def getActualLayerBounds(String name, HashMap constraints=[:]){
    getActualLayerBounds(getLayerInfoByName(name),constraints)
  }

  @Transactional
  long getTilesWithinContraints(String layerName, HashMap constraints=[:])
  {
    getTilesWithinConstraint(getLayerInfoByName(layerName), constraints)
  }

  @Transactional
  def getTilesMetaWithinContraints(TileCacheLayerInfo layer, HashMap constraints=[:])
  {
    def result = []
    def queryString = "select * from ${layer?.tileStoreTable} ${createWhereClause(constraints)}".toString()
    def metaRows = [:]
    def hashIds = []

    if(!layer) return result
    if(constraints.offset&&constraints.maxRows)
    {
      sql.eachRow(queryString, constraints.offset, constraints.maxRows){row->
        result << new TileCacheTileTableTemplate().bindSql(row)
      }
    }
    else
    {
      sql.eachRow(queryString){row->
        result << new TileCacheTileTableTemplate().bindSql(row)
      }
    }
    result
  }
  @Transactional
  def getTilesMetaWithinContraints(String layerName, HashMap constraints=[:])
  {
    getTilesMetaWithinContraints(getLayerInfoByName(layerName), constraints)
  }

  @Transactional
  long getTileCountWithinConstraint(TileCacheLayerInfo layer, HashMap constraints=[:])
  {
    String table = layer?.tileStoreTable
    def count = 0
    if(table)
    {
      def queryString = "select count(hash_id) as count from ${table} ${createWhereClause(constraints)}".toString()
      def result = sql.firstRow(queryString)

      count = result.count
    }

    count
  }

  @Transactional
  long getTileCountWithinConstraint(String layerName, HashMap constraints=[:])
  {
    getTileCountWithinConstraint(getLayerInfoByName(layerName), constraints)
  }

  @Transactional
  void writeTile(TileCacheLayerInfo layer, TileCacheImageTile tile) {

    String table = layer.tileStoreTable
    def result = sql.firstRow("select * from ${table} where hash_id = '${tile.key.hashId}'".toString())

    if (!result)
    {
      sql.executeInsert """insert into ${table} (hash_id,res,x,y,z,modified_date, bounds) values ('${tile.hashId}', ${tile.res},
                            ${tile.x}, ${tile.y},
                            ${tile.z}, '${tile.modifiedDate}', ST_GeometryFromText('${tile.bounds.polygon.g.toString()}'))""".toString()
    }
    else
    {
      sql.executeUpdate "update ${table} set modified_date = '${tile.modifiedDate}' where hash_id='${tile.hashId}'".toString()
    }

    if(tile.data)
    {
      accumuloApi.writeTile(table, tile)
    }
  }

  @Transactional
  def getMetaByKey(TileCacheLayerInfo layer, ImageTileKey key)
  {
    String table = layer.tileStoreTable
    def row = sql.firstRow("select * from ${table} where hash_id = '${key.rowId}'".toString())
    TileCacheTileTableTemplate result = new  TileCacheTileTableTemplate()

    result.bindRow(row)
  }

  @Transactional
  def getTileByMeta(TileCacheLayerInfo layer, def meta)
  {
    ImageTileKey key = new ImageTileKey([rowId:meta.hashId])

    TileCacheImageTile tile = accumuloApi.getTile(layer.tileStoreTable, key) as TileCacheImageTile

    if(meta)
    {
      tile.x      = meta.x
      tile.y      = meta.y
      tile.z      = meta.z
      tile.res    = meta.res
      if(meta.modifiedDate) tile.modifiedDate = meta.modifiedDate
      tile.bounds = new Bounds(meta?.bounds?.envelopeInternal)//new WKTReader().read(meta.bounds.toString())

    }
    else
    {
      tile = null
    }

    tile
  }

  @Transactional
  def getTileByKey(TileCacheLayerInfo layer, ImageTileKey key)
  {
    String table = layer.tileStoreTable
    TileCacheImageTile result
    def meta = sql.firstRow("select * from ${table} where hash_id = '${key.rowId}'".toString())
    if(meta)
    {
      result        = accumuloApi.getTile(table, key)
      if(result)
      {
        result.x      = meta.x?.toLong()
        result.y      = meta.y?.toLong()
        result.z      = meta.z?.toLong()
        result.res    = meta.res?.toDouble()
        if(meta.modified_date) result.modifiedDate = new Date(meta.modified_date.time)
        Geometry wktResult = new WKTReader().read(meta.bounds.toString())
        result.bounds = new Bounds(wktResult.envelopeInternal)//new WKTReader().read(meta.bounds.toString())
      }
     }


    result
  }
  @Transactional
  def getTileDataByKey(TileCacheLayerInfo layer, ImageTileKey key)
  {
    accumuloApi.getTile(layer.tileStoreTable, key)?.data
  }
}
