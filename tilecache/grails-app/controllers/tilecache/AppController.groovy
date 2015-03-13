package tilecache

import grails.converters.JSON
import groovy.sql.Sql

class AppController
{

  def grailsLinkGenerator
  def grailsApplication

  def dataSource

  def index() {}

  def client()
  {
    def sql = new Sql( dataSource )
    def tileCacheLayers = sql.rows( "select name from tile_cache_layer_info where epsg_code like '%3857'" )

    sql.close()

    [
        initParams: [
            wfsURL: grailsLinkGenerator.link( action: 'testWFS' ),
            urlProductExport: grailsLinkGenerator.link( controller: 'product', action: 'export' ),
            urlLayerActualBounds: grailsLinkGenerator.link( controller: 'accumuloProxy', action: 'actualBounds' ),
            accumuloProxyWmsURL: grailsLinkGenerator.link( controller: 'accumuloProxy', action: 'wms', absolute: true ),
            referenceLayers: grailsApplication.config.tilecache.referenceLayers,
            overlayLayers: grailsApplication.config.tilecache.overlayLayers,
            tileCacheLayers: tileCacheLayers
        ] as JSON
    ]
  }

  def admin() {}


  def testWFS()
  {
    def data = [
        "type": "FeatureCollection",
        "features": [[
            "type": "Feature",
            "geometry": [
                "type": "Polygon",
                "coordinates": [[[
                    -20037508.3428,
                    -19971868.8804
                ], [
                    -20037508.3428,
                    19971868.880408563
                ], [
                    20037508.342789244,
                    19971868.880408563
                ], [
                    20037508.342789244,
                    -19971868.8804
                ], [
                    -20037508.3428,
                    -19971868.8804
                ]
                ]]],
            "properties": [
                "name": "highres_3857",
                "id": 3,
                "tile_store_table": "omar_tilecache_highres_3857_tiles",
                "epsg_code": "EPSG:3857",
                "min_level": 0,
                "max_level": 20,
                "tile_width": 256,
                "tile_height": 256
            ]
        ]]
    ]

    render contentType: 'application/json', text: data as JSON
  }
}