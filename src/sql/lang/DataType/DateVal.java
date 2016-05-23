package sql.lang.DataType;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by clwang on 12/14/15.
 */
public class DateVal implements Value {
    Date val;

    public DateVal(Date date) {
        this.val = date;
    }

    private DateVal() {};

    @Override
    public Date getVal() { return this.val; }

    @Override
    public boolean equals(Value v) {
        return this.getVal().equals(v.getVal());
    }

    @Override
    public Value duplicate() {
        DateVal newVal = new DateVal();
        newVal.val = this.val;
        //newVal.val = (Date) this.val.clone();
        return newVal;
    }

    @Override
    public ValType getValType() {
        return ValType.DateVal;
    }

    public String toString() {
        //DateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        //return df.format(val);
        return val.toString();
    }

}
