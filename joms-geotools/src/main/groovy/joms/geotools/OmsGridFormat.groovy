package joms.geotools

import org.geotools.coverage.grid.io.AbstractGridCoverage2DReader
import org.geotools.coverage.grid.io.AbstractGridFormat
import org.geotools.coverage.grid.io.imageio.GeoToolsWriteParams
import org.geotools.factory.Hints
import org.opengis.coverage.grid.GridCoverageWriter

import joms.oms.DataInfo

/**
 * Created by sbortman on 10/22/14.
 */
class OmsGridFormat extends AbstractGridFormat
{
  @Override
  AbstractGridCoverage2DReader getReader(Object source, Hints hints = null)
  {
    return new OmsGridReader(source, hints)
  }

  @Override
  boolean accepts(Object source, Hints hints)
  {
    return DataInfo.readInfo( source?.toString() ) != null
  }

  @Override
  GeoToolsWriteParams getDefaultImageIOWriteParameters()
  {
    return null
  }

  @Override
  GridCoverageWriter getWriter(Object destination, Hints hints = null)
  {
    return null
  }
}
