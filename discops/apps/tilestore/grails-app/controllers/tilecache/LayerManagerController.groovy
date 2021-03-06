package tilecache

import grails.converters.JSON
import joms.geotools.web.HttpStatus
import org.apache.commons.collections.map.CaseInsensitiveMap

class LayerManagerController
{
   def layerManagerService

   def index()
   {

   }

   def getActualBounds(){
      def bounds = layerManagerService.getActualBounds(params)

      println bounds
      render contentType: "application/json", (bounds as JSON).toString()
   }

   /*
 def getTiles()
 {

   render ""
   def base64     = new Base64()
   def hashIds    = params.hashIds
   def family     = params.family?:""
   def qualifier  = params.qualifier?:""
   def table      = params.table
   def format     = params.format?:"image/jpeg"
   def compress   = params.compress?params.compress.toBoolean():true
   def writeType  = format.split("/")[-1]
   def hashIdList = hashIds.split(",")
   def result = []
   hashIdList.each{hashId->
     def tile = layerManagerService.getTile(table, hashId, family, qualifier)
     if(tile)
     {
       def ostream = new ByteArrayOutputStream()

       ImageIO.write( tile.getAsBufferedImage(), writeType, ostream )
       def bytes = ostream.toByteArray()
       result << [hashId:"${hashId}".toString(), image:base64.encodeToString(bytes)]
     }
   }
   def resultString = (result as JSON).toString()

   if(compress)
   {
     response.contentType = "application/x-gzip"
   }
   else
   {
     response.contentType = "application/json"
   }

   if(compress)
   {
     // ByteArrayOutputStream out = new ByteArrayOutputStream();
     GZIPOutputStream gzip = new GZIPOutputStream(response.outputStream) //out);
     gzip.write(resultString.getBytes());
     gzip.close();
   }
   else
   {
      response.outputStream.write(resultString.bytes)
   }
 //  println "SIZE == ${out.toByteArray().length}"
  // render (result as JSON).toString()
   }
 */

   /*
 def putTile()
 {
  // println "***************${params}****************"
   def hash     = params.hashId
   def family   = params.family
   def qualifier = params.qualifier
   def table = params.table

   println "BYTE LENGTH ===== ${request.inputStream.bytes.length}"


   //def img = ImageIO.read(request.inputStream)
   //println "IMAGE ======================= ${img}"

   //    println "hash: ${hash} , family:${family}, qualifier:${qualifier}, image:${img}"
   layerManagerService.writeTile(table, hash, img, family, qualifier)
 //        println "DONE WRITING!!"

 //        render "Did the putTile"
   render ""
     render ""
   }
   */

   def createOrUpdateLayer(CreateLayerCommand cmd)
   {
      if(request.JSON)
      {
         cmd.initFromJson(request.JSON)
         if(!cmd.validate())
         {
            response.status = HttpStatus.BAD_REQUEST.value
            render cmd.errors.allErrors.collect(){
               message(error:it,encodeAs:'HTML')
            } as JSON
         }
      }

      layerManagerService.createOrUpdateLayer( cmd )

      def result = layerManagerService.getLayer(cmd.name)

      if(result.status != HttpStatus.OK)
      {
         response.status = result.status.value
         render contentType: "application/json", ([message:result.message] as JSON).toString()
      }
      else
      {
         render contentType: "application/json", (result.data as JSON).toString()
      }

   }
   def createLayer(CreateLayerCommand cmd)
   {
      if(request.JSON)
      {
         cmd.initFromJson(request.JSON)
         if(!cmd.validate())
         {
            response.status = HttpStatus.BAD_REQUEST.value
            render cmd.errors.allErrors.collect(){
               message(error:it,encodeAs:'HTML')
            } as JSON
         }
      }

      def result = layerManagerService.createLayer( cmd )

      response.status = result.status.value
      if(result.status != HttpStatus.OK)
      {
         render contentType: "application/json", ([message:result.message] as JSON).toString()
      }
      else
      {
         render contentType: "application/json", (result.data as JSON).toString()
      }
   }
   def deleteLayer(def params)
   {
      def name
      CaseInsensitiveMap map = new CaseInsensitiveMap(params)

      switch(request.method.toLowerCase())
      {
         case "get":
            name = map.name
            break
         case "post":
            if(request.JSON)
            {
               name = request.JSON.name
            }
            else
            {
               name = map.name
            }
            break
         default:
            name = map.name
            break
      }

      def result = layerManagerService.deleteLayer(name)
      response.status = result.status.value
      if(response.status != HttpStatus.OK)
      {
         render contentType: "application/json", ([message:result.message] as JSON).toString()
      }
      else
      {
         render contentType: "application/json", (result.data as JSON).toString()
      }
   }

   def renameLayer(RenameLayerCommand cmd)//String oldName, String newName)
   {

        println cmd
        // CaseInsensitiveMap map = new CaseInsensitiveMap(params)
      if(request.JSON)
      {
         cmd.initFromJson(request.JSON)
         if(!cmd.validate())
         {
            response.status = HttpStatus.BAD_REQUEST.value
            render cmd.errors.allErrors.collect(){
               message(error:it,encodeAs:'HTML')
            } as JSON
         }
      }
      layerManagerService.renameLayer( cmd.oldName, cmd.newName)

      def result = layerManagerService.getLayer(cmd.newName?:"")

      response.status = result.status.value
      if(result.status == HttpStatus.OK)
      {
         render contentType: "application/json", (result.data as JSON).toString()
      }
      else
      {
         render contentType: "text/plain", result.message
      }
   }

   def getLayer()
   {
      def result = layerManagerService.getLayer(params.name?:"")
      response.status = result.status.value

      if(result.status == HttpStatus.OK)
      {
         render contentType: "application/json", (result.data as JSON).toString()
      }
      else
      {
         render contentType: "text/plain", result.message
      }
   }
   def getLayers()
   {
      def result = layerManagerService.getLayers()

      if(result.status == HttpStatus.OK)
      {
         render contentType: "application/json", (result.data as JSON).toString()
      }
      else
      {
         render contentType: "text/plain", result.message
      }
   }
}
