package sql.lang.DataType;

import java.sql.Time;

/**
 * Created by clwang on 12/14/15.
 */
public class TimeVal implements Value {
    Time val;
    String raw;

    public TimeVal(Time time) {
        this.val = time;
        this.raw = time.toString();
    }

    public void setRaw(String raw) {
        this.raw = raw;
    }

    @Override
    public String getRaw() {
        return this.raw;
    }

    @Override
    public Time getVal() { return this.val; }

    @Override
    public boolean equals(Value v) {
        if (v instanceof TimeVal) {
            return this.val.equals(v.getVal());
        }
        return false;
    }

    @Override
    public String toString() {
        return val.toString();
    }

    public TimeVal duplicate() {
        return new TimeVal((Time) this.val.clone());
    }

    @Override
    public ValType getValType() {
        return ValType.TimeVal;
    }

}