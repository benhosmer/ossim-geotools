package tilecache.wms

import geoscript.geom.Bounds
import geoscript.geom.io.WktReader
import geoscript.render.Map as GeoScriptMap
import groovy.sql.Sql
import groovy.xml.StreamingMarkupBuilder
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.springframework.beans.factory.InitializingBean

class WebMappingService implements InitializingBean
{
  static transactional = false

  def baseUrl
  def wmsUrl
  def dtdUrl

  def dataSourceUnproxied

  LinkGenerator grailsLinkGenerator
  def layerManagerService

  private static final def getMapFormats = [
      'image/png',
      'image/png8',
      'image/png; mode=8bit',
      'image/geotiff',
      'image/geotiff8',
      'image/gif',
      'image/jpeg',
      'image/tiff',
      'image/tiff8'
  ]

  private static final getFeatureInfoFormats = [
      'text/plain',
      'application/vnd.ogc.gml',
      'application/vnd.ogc.gml/3.1.1',
      'text/html',
      'application/json'
  ]

  private static final supportedProjections = [
      'EPSG:3857',
      'EPSG:4326'
  ]

  private static final exceptionFormats = [
      'application/vnd.ogc.se_xml',
      'application/vnd.ogc.se_inimage'
  ]

//  private static final layers = [[
//      queryable: '1',
//      opaque: '0',
//      name: '',
//      title: '',
//      description: '',
//      keywords: [],
//      projection: '',
//      geoMinX: '',
//      geoMinY: '',
//      geoMaxX: '',
//      geoMaxY: '',
//      minX: '',
//      minY: '',
//      maxX: '',
//      maxY: '',
//      styles: [[
//          name: '',
//          title: '',
//          description: '',
//          legend: [
//              width: '',
//              height: '',
//              format: '',
//              legendUrl: ''
//          ]
//      ]]
//  ]]

  def getMap(GetMapCommand cmd)
  {
    def startTime = System.currentTimeMillis()
    GeoScriptMap map
    def contentType = cmd.format
    def result = new ByteArrayOutputStream()
    def element
    //println "_______________________________"
    //println cmd
    //println "_______________________________"
    try
    {
      def layers = layerManagerService.createTileLayers( cmd.layers?.split( ',' ) )
      //def img = ImageIO.read("/Volumes/DataDrive/data/earth2.tif" as File)
      // BufferedImage dest = img.getSubimage(0, 0, cmd.width, cmd.height);

      // ImageIO.write(dest, cmd.format.split('/')[-1],response.outputStream)
      //img = null

      element = layerManagerService.createSession()

      map = new GeoScriptMap(
          width: cmd.width,
          height: cmd.height,
          proj: cmd.srs,
          type: cmd.format.split( '/' )[-1],
          bounds: cmd.bbox.split( "," ).collect() { it.toDouble() } as Bounds, // [-180, -90, 180, 90] as Bounds,
          // backgroundColor:cmd.bgcolor,
          layers: layers
      )

      // def gzipped = new GZIPOutputStream(result)
      //  OutputStreamWriter writer=new OutputStreamWriter(gzipped);
      map?.render( result )
      //gzipped.finish();
      //writer.close();

    }
    catch ( def e )
    {
      // really need to write exception to stream

      e.printStackTrace()
    }
    finally
    {
      if ( element != null )
      {
        layerManagerService.deleteSession( element )
      }

      // map?.layers.each{it.dispose()}
      map?.close()
    }
    // println "Time: ${(System.currentTimeMillis()-startTime)/1000} seconds"
    //println result.toByteArray().size()

    [contentType: contentType, buffer: result.toByteArray()]
    // println "Done GetMap ${tempId}"
  }

  def getCapabilities(GetCapabilitiesCommand getMapCmd)
  {

    def x = {
      mkp.xmlDeclaration()
      mkp.yieldUnescaped """<!DOCTYPE WMT_MS_Capabilities SYSTEM "${dtdUrl}">"""
      WMT_MS_Capabilities( version: '1.1.1' ) {
        Servce {
          Name()
          Title()
          Abstract()
          KeywordList {
            Keyword()
          }
          OnlineResource( 'xmlns:xlink': "http://www.w3.org/1999/xlink", 'xlink:type': "simple",
              'xlink:href': "${baseUrl}" )
          ContactInformation {
            ContactPersonPrimary {
              ContactPerson()
              ContactOrganization()
            } /* ContactPersonPrimary */
            ContactAddress {
              AddressType()
              Address()
              City()
              StateOrProvince()
              PostCode()
              Country()
            } /* ContactAddress */
            ContactVoiceTelephone()
            ContactFacsimileTelephone()
            ContactElectronicMailAddress()
          } /* ContactInformation */
          Fees()
          AccessConstraints()
        } /* Servce */
        Capability {
          Request {
            GetCapabilities {
              Format( 'application/vnd.ogc.wms_xml' )
              DCPType {
                HTTP {
                  Get {
                    OnlineResource( 'xmlns:xlink': "http://www.w3.org/1999/xlink",
                        'xlink:type': "simple", 'xlink:href': wmsUrl )
                  } /* Get */
                } /* HTTP */
              } /* DCPType */
              DCPType {
                HTTP {
                  Post {
                    OnlineResource( 'xmlns:xlink': "http://www.w3.org/1999/xlink",
                        'xlink:type': "simple", 'xlink:href': wmsUrl )
                  } /* Post */
                } /* HTTP */
              } /* DCPType */
            } /* GetCapabilities */
            GetMap {
              getMapFormats.each { Format( it ) }
              DCPType {
                HTTP {
                  Get {
                    OnlineResource( 'xmlns:xlink': "http://www.w3.org/1999/xlink",
                        'xlink:type': "simple", 'xlink:href': wmsUrl )
                  } /* Get */
                } /* HTTP */
              } /* DCPType */
              DCPType {
                HTTP {
                  Post {
                    OnlineResource( 'xmlns:xlink': "http://www.w3.org/1999/xlink",
                        'xlink:type': "simple", 'xlink:href': wmsUrl )
                  } /* Post */
                } /* HTTP */
              } /* DCPType */
            } /* GetMap */
            GetFeatureInfo {
              getFeatureInfoFormats.each { Format( it ) }
              DCPType {
                HTTP {
                  Get {
                    OnlineResource( 'xmlns:xlink': "http://www.w3.org/1999/xlink",
                        'xlink:type': "simple", 'xlink:href': wmsUrl )
                  } /* Get */
                } /* HTTP */
              } /* DCPType */
              DCPType {
                HTTP {
                  Post {
                    OnlineResource( 'xmlns:xlink': "http://www.w3.org/1999/xlink",
                        'xlink:type': "simple", 'xlink:href': wmsUrl )
                  } /* Post */
                } /* HTTP */
              } /* DCPType */
            } /* GetFeatureInfo */
            DescribeLayer {
              Format( 'application/vnd.ogc.wms_xml' )
              DCPType {
                HTTP {
                  Get {
                    OnlineResource( 'xmlns:xlink': "http://www.w3.org/1999/xlink",
                        'xlink:type': "simple", 'xlink:href': wmsUrl )
                  } /* Get */
                } /* HTTP */
              } /* DCPType */
            } /* DescribeLayer */
            GetLegendGraphic {
              Format( 'image/png' )
              Format( 'image/jpeg' )
              Format( 'image/gif' )
              DCPType {
                HTTP {
                  Get {
                    OnlineResource( 'xmlns:xlink': "http://www.w3.org/1999/xlink",
                        'xlink:type': "simple", 'xlink:href': wmsUrl )
                  } /* Get */
                } /* HTTP */
              } /* DCPType */
            } /* GetLegendGraphic */
            GetStyles {
              Format( 'image/png' )
              Format( 'image/jpeg' )
              Format( 'image/gif' )
              DCPType {
                HTTP {
                  Get {
                    OnlineResource( 'xmlns:xlink': "http://www.w3.org/1999/xlink",
                        'xlink:type': "simple", 'xlink:href': wmsUrl )
                  } /* Get */
                } /* HTTP */
              } /* DCPType */
            } /* GetStyles */
          } /* Request */
        } /* Capability */
        "Exception" {
          exceptionFormats.each { Format( it ) }
        } /* Exception */
        UserDefinedSymbolization( SupportSLD: '1', UserLayer: '1', UserStyle: '1', RemoteWFS: '1' )
        Layer {
          Title()
          Abstract()
          supportedProjections?.each { SRS( it ) }
          LatLonBoundingBox( minx: '-180.0', miny: '-90.0', maxx: '180.0', maxy: '90.0' )
          getLayers()?.each { layer ->
            Layer( queryable: layer?.queryable, opaque: layer?.opaque ) {
              Name( layer?.name )
              Title( layer?.title )
              Abstract( layer?.description )
              KeywordList {
                layer?.keywords?.each { Keyword( it ) }
              } /* KeywordList */
              SRS( layer?.projection )
              LatLonBoundingBox( minx: layer.geoMinX, miny: layer?.geoMinY, maxx: layer?.geoMaxX, maxy: layer?.geoMaxY )
              BoundingBox( SRS: layer?.projection, minx: layer?.minX, miny: layer?.minY, maxx: layer?.maxX, maxy: layer?.maxY )
              layer?.styles?.each { style ->
                Style {
                  Name( style?.name )
                  Title( style?.title )
                  Abstract( style?.description )
                  LegendURL( width: style?.legend?.width, height: style?.legend?.height ) {
                    Format( style?.legend?.format )
                    OnlineResource( 'xmlns:xlink': 'http://www.w3.org/1999/xlink',
                        'xlink:type': 'simple', 'xlink:href': "${wmsUrl}${style?.legend?.legendUrl}" )
                  } /* LegendURL */
                } /* Style */
              } /* layer.styles */
            } /* Layer */
          } /* layers */
        } /* Layer */
      } /* WMT_MS_Capabilities */
    }

    def builder = new StreamingMarkupBuilder()
    def doc = builder.bind( x )

    [contentType: 'application/vnd.ogc.wms_xml', buffer: doc.toString().bytes]
  }

  private def getLayers()
  {
    def sql = Sql.newInstance(dataSourceUnproxied)
    def wktReader = new WktReader()

    def q = """
    select id, name, st_astext(bounds) as bounds, epsg_code,
        min_level, max_level, tile_width, tile_height, tile_store_table
    from tile_cache_layer_info
    """

    def layers = []

    sql.eachRow(q) { row ->
      def geom = wktReader.read(row.bounds)
      def bounds = geom.bounds

      bounds.proj = row.epsg_code

      def layerInfo = [
          queryable: '1',
          opaque: '0',
          name: row.name,
          title: row.name,
          description: '',
          keywords: [],
          projection: row.epsg_code,
          geoMinX: '-180', geoMinY: '-90', geoMaxX: '180', geoMaxY: '90',
          minX: bounds.minX, minY: bounds.minY, maxX: bounds.maxX, maxY: bounds.maxY,
          styles: []
      ]

      layers << layerInfo
    }

    sql?.close()

    layers
  }

  @Override
  void afterPropertiesSet() throws Exception
  {
    baseUrl = grailsLinkGenerator.serverBaseURL
    wmsUrl = "${baseUrl}/wms"
    dtdUrl = "${baseUrl}/schemas/wms/1.1.1/WMS_MS_Capabilities.dtd"
  }
}
