package org.ossim.kettle.steps.datainfoindexer;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.trans.step.BaseStepData;
import org.pentaho.di.trans.step.StepDataInterface;


public class DataInfoIndexerData extends BaseStepData implements StepDataInterface
{
	public RowMetaInterface outputRowMeta;

    public DataInfoIndexerData()
	{
		super();
	}
}
