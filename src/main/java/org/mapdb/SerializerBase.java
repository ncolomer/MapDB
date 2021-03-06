/*
 *  Copyright (c) 2012 Jan Kotek
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.mapdb;

import java.io.*;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/**
 * Serializer which uses 'header byte' to serialize/deserialize
 * most of classes from 'java.lang' and 'java.util' packages.
 *
 * @author Jan Kotek
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
public class SerializerBase implements Serializer{


    static final class knownSerializable{
        static final Set get = new HashSet(Arrays.asList(
            BTreeKeySerializer.STRING,
            BTreeKeySerializer.ZERO_OR_POSITIVE_LONG,
            BTreeKeySerializer.ZERO_OR_POSITIVE_INT,
            Utils.COMPARABLE_COMPARATOR, Utils.COMPARABLE_COMPARATOR_WITH_NULLS,

            Serializer.STRING_NOSIZE, Serializer.LONG, Serializer.INTEGER,
            Serializer.EMPTY_SERIALIZER, Serializer.BASIC, Serializer.BOOLEAN,
            Serializer.BYTE_ARRAY_NOSIZE
    ));
    }

    public static void assertSerializable(Object o){
        if(o!=null && !(o instanceof Serializable)
                && !knownSerializable.get.contains(o)){
            throw new IllegalArgumentException("Not serializable: "+o.getClass());
        }
    }

    /**
     * Utility class similar to ArrayList, but with fast identity search.
     */
    protected final static class FastArrayList<K> {

        public int size ;
        public K[] data ;

        public FastArrayList(){
            size=0;
            data = (K[]) new Object[1];
        }

        public boolean forwardRefs = false;


        public void add(K o) {
            if (data.length == size) {
                //grow array if necessary
                data = Arrays.copyOf(data, data.length * 2);
            }

            data[size] = o;
            size++;
        }



        /**
         * This method is reason why ArrayList is not used.
         * Search an item in list and returns its index.
         * It uses identity rather than 'equalsTo'
         * One could argue that TreeMap should be used instead,
         * but we do not expect large object trees.
         * This search is VERY FAST compared to Maps, it does not allocate
         * new instances or uses method calls.
         *
         * @param obj to find in list
         * @return index of object in list or -1 if not found
         */
        public int identityIndexOf(Object obj) {
            for (int i = 0; i < size; i++) {
                if (obj == data[i]){
                    forwardRefs = true;
                    return i;
                }
            }
            return -1;
        }

    }




    @Override
    public void serialize(final DataOutput out, final Object obj) throws IOException {
        serialize(out, obj, null);
    }


    public void serialize(final DataOutput out, final Object obj, FastArrayList<Object> objectStack) throws IOException {

        /**try to find object on stack if it exists*/
        if (objectStack != null) {
            int indexInObjectStack = objectStack.identityIndexOf(obj);
            if (indexInObjectStack != -1) {
                //object was already serialized, just write reference to it and return
                out.write(Header.OBJECT_STACK);
                Utils.packInt(out, indexInObjectStack);
                return;
            }
            //add this object to objectStack
            objectStack.add(obj);
        }

        final Class clazz = obj != null ? obj.getClass() : null;

        /** first try to serialize object without initializing object stack*/
        if (obj == null) {
            out.write(Header.NULL);
            return;
        } else if (clazz == Boolean.class) {
            if ((Boolean) obj)
                out.write(Header.BOOLEAN_TRUE);
            else
                out.write(Header.BOOLEAN_FALSE);
            return;
        } else if (clazz == Integer.class) {
            int val = (Integer) obj;
            if(val>=-9 && val<=16){
                out.write(Header.INT_M9 + (val + 9));
            }else if (val == Integer.MIN_VALUE){
                out.write(Header.INT_MIN_VALUE);
            }else if (val == Integer.MAX_VALUE){
                out.write(Header.INT_MAX_VALUE);
            }else if ((val&0xFF)==val) {
                out.write(Header.INT_F1);
                out.write(val);
            }else if (((-val)&0xFF)==val) {
                out.write(Header.INT_MF1);
                out.write(-val);
            }else if ((val&0xFFFF)==val) {
                out.write(Header.INT_F2);
                out.write(val & 0xFF);
                out.write(val>>>8&0xFF);
            }else if (((-val)&0xFFFF)==val) {
                out.write(Header.INT_MF2);
                val-=val;
                out.write(val&0xFF);
                out.write(val>>>8&0xFF);
            }else if ((val&0xFFFFFF)==val) {
                out.write(Header.INT_F3);
                out.write(val & 0xFF);
                out.write(val>>>8&0xFF);
                out.write(val>>>16&0xFF);
            }else if (((-val)&0xFFFFFF)==val) {
                out.write(Header.INT_MF3);
                val-=val;
                out.write(val&0xFF);
                out.write((val>>>8)&0xFF);
                out.write((val>>>16)&0xFF);
            } else{
                out.write(Header.INT);
                out.writeInt(val);
            }
            return;
        } else if (clazz == Long.class) {
            long val = (Long) obj;
            if(val>=-9 && val<=16){
                out.write((int) (Header.LONG_M9 + (val + 9)));
            }else if (val == Long.MIN_VALUE){
                out.write(Header.LONG_MIN_VALUE);
            }else if (val == Long.MAX_VALUE){
                out.write(Header.LONG_MAX_VALUE);
            }else if ((val&0xFFL)==val) {
                out.write(Header.LONG_F1);
                out.write((int) val);
            }else if (((-val)&0xFFL)==val) {
                out.write(Header.LONG_MF1);
                out.write((int) -val);
            }else if ((val&0xFFFFL)==val) {
                out.write(Header.LONG_F2);
                out.write((int) (val & 0xFF));
                out.write((int) (val>>>8&0xFF));
            }else if (((-val)&0xFFFFL)==val) {
                out.write(Header.LONG_MF2);
                val-=val;
                out.write((int) (val&0xFF));
                out.write((int) (val>>>8&0xFF));
            }else if ((val&0xFFFFFFL)==val) {
                out.write(Header.LONG_F3);
                out.write((int) (val & 0xFF));
                out.write((int) (val>>>8&0xFF));
                out.write((int) (val>>>16&0xFF));
            }else if (((-val)&0xFFFFFFL)==val) {
                out.write(Header.LONG_MF3);
                val-=val;
                out.write((int) (val&0xFF));
                out.write((int) ((val>>>8)&0xFF));
                out.write((int) ((val>>>16)&0xFF));
            }else if ((val&0xFFFFFFFFL)==val) {
                out.write(Header.LONG_F4);
                out.write((int) (val & 0xFF));
                out.write((int) (val>>>8&0xFF));
                out.write((int) (val>>>16&0xFF));
                out.write((int) (val>>>24&0xFF));
            }else if (((-val)&0xFFFFFFFFL)==val) {
                out.write(Header.LONG_MF4);
                val-=val;
                out.write((int) (val&0xFF));
                out.write((int) ((val>>>8)&0xFF));
                out.write((int) ((val>>>16)&0xFF));
                out.write((int) ((val>>>24)&0xFF));
            }else if ((val&0xFFFFFFFFFFL)==val) {
                out.write(Header.LONG_F5);
                out.write((int) (val & 0xFF));
                out.write((int) (val>>>8&0xFF));
                out.write((int) (val>>>16&0xFF));
                out.write((int) (val>>>24&0xFF));
                out.write((int) (val>>>32&0xFF));
            }else if (((-val)&0xFFFFFFFFFFL)==val) {
                out.write(Header.LONG_MF5);
                val-=val;
                out.write((int) (val&0xFF));
                out.write((int) ((val>>>8)&0xFF));
                out.write((int) ((val>>>16)&0xFF));
                out.write((int) ((val>>>24)&0xFF));
                out.write((int) ((val>>>32)&0xFF));
            }else if ((val&0xFFFFFFFFFFFFL)==val) {
                out.write(Header.LONG_F6);
                out.write((int) (val & 0xFF));
                out.write((int) (val>>>8&0xFF));
                out.write((int) (val>>>16&0xFF));
                out.write((int) (val>>>24&0xFF));
                out.write((int) (val>>>32&0xFF));
                out.write((int) (val>>>40&0xFF));
            }else if (((-val)&0xFFFFFFFFFFFFL)==val) {
                out.write(Header.LONG_MF6);
                val-=val;
                out.write((int) (val&0xFF));
                out.write((int) ((val>>>8)&0xFF));
                out.write((int) ((val>>>16)&0xFF));
                out.write((int) ((val>>>24)&0xFF));
                out.write((int) ((val>>>32)&0xFF));
                out.write((int) ((val>>>40)&0xFF));
            }else if ((val&0xFFFFFFFFFFFFFFL)==val) {
                out.write(Header.LONG_F7);
                out.write((int) (val & 0xFF));
                out.write((int) (val>>>8&0xFF));
                out.write((int) (val>>>16&0xFF));
                out.write((int) (val>>>24&0xFF));
                out.write((int) (val>>>32&0xFF));
                out.write((int) (val>>>40&0xFF));
                out.write((int) (val>>>48&0xFF));
            }else if (((-val)&0xFFFFFFFFFFFFL)==val) {
                out.write(Header.LONG_MF7);
                val-=val;
                out.write((int) (val&0xFF));
                out.write((int) ((val>>>8)&0xFF));
                out.write((int) ((val>>>16)&0xFF));
                out.write((int) ((val>>>24)&0xFF));
                out.write((int) ((val>>>32)&0xFF));
                out.write((int) ((val>>>40)&0xFF));
                out.write((int) ((val>>>48)&0xFF));

            } else{
                out.write(Header.LONG);
                out.writeLong(val);
            }
            return;
        } else if (clazz == Byte.class) {
            byte val = (Byte) obj;
            if (val == -1)
                out.write(Header.BYTE_M1);
            else if (val == 0)
                out.write(Header.BYTE_0);
            else if (val == 1)
                out.write(Header.BYTE_1);
            else {
                out.write(Header.BYTE);
                out.writeByte(val);
            }
            return;
        } else if (clazz == Character.class) {
            char val = (Character)obj;
            if(val==0){
                out.write(Header.CHAR_0);
            }else if(val==1){
                out.write(Header.CHAR_1);
            }else if (val<=255){
                out.write(Header.CHAR_255);
                out.write(val);
            }else{
                out.write(Header.CHAR);
                out.writeChar((Character) obj);
            }
            return;
        } else if (clazz == Short.class) {
            short val = (Short) obj;
            if (val == -1){
                out.write(Header.SHORT_M1);
            }else if (val == 0){
                out.write(Header.SHORT_0);
            }else if (val == 1){
                out.write(Header.SHORT_1);
            }else if (val > 0 && val < 255) {
                out.write(Header.SHORT_255);
                out.write(val);
            }else if (val < 0 && val > -255) {
                out.write(Header.SHORT_M255);
                out.write(-val);
            } else {
                out.write(Header.SHORT);
                out.writeShort(val);
            }
            return;
        } else if (clazz == Float.class) {
            float v = (Float) obj;
            if (v == -1f)
                out.write(Header.FLOAT_M1);
            else if (v == 0f)
                out.write(Header.FLOAT_0);
            else if (v == 1f)
                out.write(Header.FLOAT_1);
            else if (v >= 0 && v <= 255 && (int) v == v) {
                out.write(Header.FLOAT_255);
                out.write((int) v);
            } else if (v >= Short.MIN_VALUE && v <= Short.MAX_VALUE && (short) v == v) {
                out.write(Header.FLOAT_SHORT);
                out.writeShort((int) v);
            } else {
                out.write(Header.FLOAT);
                out.writeFloat(v);
            }
            return;
        } else if (clazz == Double.class) {
            double v = (Double) obj;
            if (v == -1D){
                out.write(Header.DOUBLE_M1);
            }else if (v == 0D){
                out.write(Header.DOUBLE_0);
            }else if (v == 1D){
                out.write(Header.DOUBLE_1);
            }else if (v >= 0 && v <= 255 && (int) v == v) {
                out.write(Header.DOUBLE_255);
                out.write((int) v);
            } else if (v >= Short.MIN_VALUE && v <= Short.MAX_VALUE && (short) v == v) {
                out.write(Header.DOUBLE_SHORT);
                out.writeShort((int) v);
            } else if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE && (int) v == v) {
                out.write(Header.DOUBLE_INT);
                out.writeInt((int) v);
            } else {
                out.write(Header.DOUBLE);
                out.writeDouble(v);
            }
             return;
        } else if (obj instanceof byte[]) {
            byte[] b = (byte[]) obj;
            serializeByteArray(out, b);
            return;

        } else if (obj instanceof boolean[]) {
            out.write(Header.ARRAY_BOOLEAN);
            boolean[] a_bool = (boolean[]) obj;
            Utils.packInt(out, a_bool.length);//write the number of booleans not the number of bytes
            byte[] a = booleanToByteArray(a_bool);
            out.write(a);
            return;
        } else if (obj instanceof short[]) {
            out.write(Header.ARRAY_SHORT);
            short[] a = (short[]) obj;
            Utils.packInt(out, a.length);
            for(short s:a) out.writeShort(s);
            return;
        } else if (obj instanceof char[]) {
            out.write(Header.ARRAY_CHAR);
            char[] a = (char[]) obj;
            Utils.packInt(out, a.length);
            for(char s:a) out.writeChar(s);
            return;
        } else if (obj instanceof float[]) {
            out.write(Header.ARRAY_FLOAT);
            float[] a = (float[]) obj;
            Utils.packInt(out, a.length);
            for(float s:a) out.writeFloat(s);
            return;
        } else if (obj instanceof double[]) {
            out.write(Header.ARRAY_DOUBLE);
            double[] a = (double[]) obj;
            Utils.packInt(out, a.length);
            for(double s:a) out.writeDouble(s);
            return;
        } else if (obj instanceof int[]) {
            int[] val = (int[]) obj;
            int max = Integer.MIN_VALUE;
            int min = Integer.MAX_VALUE;
            for (int i : val) {
                max = Math.max(max, i);
                min = Math.min(min, i);
            }
            if (Byte.MIN_VALUE<=min && max<=Byte.MAX_VALUE) {
                out.write(Header.ARRAY_INT_BYTE);
                Utils.packInt(out, val.length);
                for (int i : val) out.write(i);
            }else if (Short.MIN_VALUE <= min && max <= Short.MAX_VALUE){
                out.write(Header.ARRAY_INT_SHORT);
                Utils.packInt(out, val.length);
                for (int i : val) out.writeShort(i);
            } else if (0 <= min) {
                out.write(Header.ARRAY_INT_PACKED);
                Utils.packInt(out, val.length);
                for (int l : val) Utils.packInt(out, l);
            } else {
                out.write(Header.ARRAY_INT);
                Utils.packInt(out, val.length);
                for (int i : val) out.writeInt(i);
            }
            return;
        } else if (obj instanceof long[]) {
            long[] val = (long[]) obj;
            long max = Long.MIN_VALUE;
            long min = Long.MAX_VALUE;
            for (long i : val) {
                max = Math.max(max, i);
                min = Math.min(min, i);
            }
            if (Byte.MIN_VALUE<=min && max<=Byte.MAX_VALUE) {
                out.write(Header.ARRAY_LONG_BYTE);
                Utils.packInt(out, val.length);
                for (long i : val) out.write((int) i);
            }else if (Short.MIN_VALUE <= min && max <= Short.MAX_VALUE){
                out.write(Header.ARRAY_LONG_SHORT);
                Utils.packInt(out, val.length);
                for (long i : val) out.writeShort((int) i);
            } else if (0 <= min) {
                out.write(Header.ARRAY_LONG_PACKED);
                Utils.packInt(out, val.length);
                for (long l : val) Utils.packLong(out, l);
            }else if (Integer.MIN_VALUE <= min && max <= Integer.MAX_VALUE){
                out.write(Header.ARRAY_LONG_INT);
                Utils.packInt(out, val.length);
                for (long i : val) out.writeInt((int) i);
            } else {
                out.write(Header.ARRAY_LONG);
                Utils.packInt(out, val.length);
                for (long i : val) out.writeLong(i);
            }
            return;
        } else if (clazz == String.class) {
            String val = (String) obj;
            int len = val.length();
            if(len == 0){
                out.write(Header.STRING_0);
            }else{
                if (len<=10){
                    out.write(Header.STRING_0+len);
                }else{
                    out.write(Header.STRING);
                    Utils.packInt(out,len);
                }
                //TODO investigate if c could be negative here
                //TODO how about characters over 65K
                for (int i = 0; i < len; i++)
                    Utils.packInt(out,(int)((String) obj).charAt(i));
            }
            return;
        } else if (clazz == BigInteger.class) {
            out.write(Header.BIGINTEGER);
            byte[] buf = ((BigInteger) obj).toByteArray();
            Utils.packInt(out, buf.length);
            out.write(buf);
            return;
        } else if (clazz == BigDecimal.class) {
            out.write(Header.BIGDECIMAL);
            BigDecimal d = (BigDecimal) obj;
            byte[] buf = d.unscaledValue().toByteArray();
            Utils.packInt(out, buf.length);
            out.write(buf);
            Utils.packInt(out, d.scale());
            return;
        } else if (obj instanceof Class) {
            out.write(Header.CLASS);
            serializeClass(out, (Class) obj);
            return;
        } else if (clazz == Date.class) {
            out.write(Header.DATE);
            out.writeLong(((Date) obj).getTime());
            return;
        } else if (clazz == UUID.class) {
            out.write(Header.UUID);
            out.writeLong(((UUID) obj).getMostSignificantBits());
            out.writeLong(((UUID)obj).getLeastSignificantBits());
            return;
        } else if(obj == Fun.HI){
            out.write(Header.FUN_HI);
        } else if(clazz == BTreeKeySerializer.BasicKeySerializer.class){
            out.write(Header.MAPDB);
            Utils.packInt(out, HeaderMapDB.B_TREE_BASIC_KEY_SERIALIZER);
            assert(((BTreeKeySerializer.BasicKeySerializer)obj).defaultSerializer==this);
            return;
        } else if(obj == BTreeKeySerializer.ZERO_OR_POSITIVE_LONG){
            out.write(Header.MAPDB);
            Utils.packInt(out, HeaderMapDB.B_TREE_SERIALIZER_POS_LONG);
            return;
        } else if(obj == BTreeKeySerializer.ZERO_OR_POSITIVE_INT){
            out.write(Header.MAPDB);
            Utils.packInt(out, HeaderMapDB.B_TREE_SERIALIZER_POS_INT);
            return;
        } else if(obj == Serializer.STRING_NOSIZE){
            out.write(Header.MAPDB);
            Utils.packInt(out, HeaderMapDB.STRING_SERIALIZER);
            return;
        } else if(obj == Serializer.LONG){
            out.write(Header.MAPDB);
            Utils.packInt(out,HeaderMapDB.LONG_SERIALIZER);
            return;
        } else if(obj == Serializer.INTEGER){
            out.write(Header.MAPDB);
            Utils.packInt(out, HeaderMapDB.INT_SERIALIZER);
            return;
        } else if(obj == Serializer.EMPTY_SERIALIZER){
            out.write(Header.MAPDB);
            Utils.packInt(out, HeaderMapDB.EMPTY_SERIALIZER);
            return;
        } else if(obj == BTreeKeySerializer.STRING){
            out.write(Header.MAPDB);
            Utils.packInt(out, HeaderMapDB.B_TREE_SERIALIZER_STRING);
            return;
        } else if(obj == Utils.COMPARABLE_COMPARATOR){
            out.write(Header.MAPDB);
            Utils.packInt(out, HeaderMapDB.COMPARABLE_COMPARATOR);
            return;
        } else if(obj == Utils.COMPARABLE_COMPARATOR_WITH_NULLS){
            out.write(Header.MAPDB);
            Utils.packInt(out, HeaderMapDB.COMPARABLE_COMPARATOR_WITH_NULLS);
            return;
        } else if(obj == BASIC){
            out.write(Header.MAPDB);
            Utils.packInt(out,HeaderMapDB.BASIC_SERIALIZER);
            return;
        } else if(obj == BOOLEAN){
            out.write(Header.MAPDB);
            Utils.packInt(out,HeaderMapDB.BOOLEAN_SERIALIZER);
            return;
        } else if(obj == BYTE_ARRAY_NOSIZE){
            out.write(Header.MAPDB);
            Utils.packInt(out,HeaderMapDB.SERIALIZER_BYTE_ARRAY_NOSIZE);
            return;

        } else if(obj == this){
            out.write(Header.MAPDB);
            Utils.packInt(out, HeaderMapDB.THIS_SERIALIZER);
            return;
        }




        /** classes bellow need object stack, so initialize it if not alredy initialized*/
        if (objectStack == null) {
            objectStack = new FastArrayList();
            objectStack.add(obj);
        }


        if (obj instanceof Object[]) {
            Object[] b = (Object[]) obj;
            boolean packableLongs = b.length <= 255;
            boolean allNull = true;
            if (packableLongs) {
                //check if it contains packable longs
                for (Object o : b) {
                    if(o!=null){
                        allNull=false;
                        if (o.getClass() != Long.class || ((Long) o < 0 && (Long) o != Long.MAX_VALUE)) {
                            packableLongs = false;
                        }
                    }

                    if(!packableLongs && !allNull)
                        break;
                }
            }else{
                //check for all null
                for (Object o : b) {
                    if(o!=null){
                        allNull=false;
                        break;
                    }
                }
            }
            if(allNull){
                out.write(Header.ARRAY_OBJECT_ALL_NULL);
                Utils.packInt(out, b.length);

                // Write classfor components
                Class<?> componentType = obj.getClass().getComponentType();
                serializeClass(out, componentType);

            }else if (packableLongs) {
                //packable Longs is special case,  it is often used in MapDB to reference fields
                out.write(Header.ARRAY_OBJECT_PACKED_LONG);
                out.write(b.length);
                for (Object o : b) {
                    if (o == null)
                        Utils.packLong(out, 0);
                    else
                        Utils.packLong(out, (Long) o + 1);
                }

            } else {
                out.write(Header.ARRAY_OBJECT);
                Utils.packInt(out, b.length);

                // Write classfor components
                Class<?> componentType = obj.getClass().getComponentType();
                serializeClass(out, componentType);

                for (Object o : b)
                    serialize(out, o, objectStack);

            }

        } else if (clazz == ArrayList.class) {
            ArrayList l = (ArrayList) obj;
            boolean packableLongs = l.size() < 255;
            if (packableLongs) {
                //packable Longs is special case,  it is often used in MapDB to reference fields
                for (Object o : l) {
                    if (o != null && (o.getClass() != Long.class || ((Long) o < 0 && (Long) o != Long.MAX_VALUE))) {
                        packableLongs = false;
                        break;
                    }
                }
            }
            if (packableLongs) {
                out.write(Header.ARRAYLIST_PACKED_LONG);
                out.write(l.size());
                for (Object o : l) {
                    if (o == null)
                        Utils.packLong(out, 0);
                    else
                        Utils.packLong(out, (Long) o + 1);
                }
            } else {
                serializeCollection(Header.ARRAYLIST, out, obj, objectStack);
            }

        } else if (clazz == java.util.LinkedList.class) {
            serializeCollection(Header.LINKEDLIST, out, obj, objectStack);
        } else if (clazz == TreeSet.class) {
            TreeSet l = (TreeSet) obj;
            out.write(Header.TREESET);
            Utils.packInt(out, l.size());
            serialize(out, l.comparator(), objectStack);
            for (Object o : l)
                serialize(out, o, objectStack);
        } else if (clazz == HashSet.class) {
            serializeCollection(Header.HASHSET, out, obj, objectStack);
        } else if (clazz == LinkedHashSet.class) {
            serializeCollection(Header.LINKEDHASHSET, out, obj, objectStack);
        } else if (clazz == TreeMap.class) {
            TreeMap l = (TreeMap) obj;
            out.write(Header.TREEMAP);
            Utils.packInt(out, l.size());
            serialize(out, l.comparator(), objectStack);
            for (Object o : l.keySet()) {
                serialize(out, o, objectStack);
                serialize(out, l.get(o), objectStack);
            }
        } else if (clazz == HashMap.class) {
            serializeMap(Header.HASHMAP, out, obj, objectStack);
        } else if (clazz == LinkedHashMap.class) {
            serializeMap(Header.LINKEDHASHMAP, out, obj, objectStack);
        } else if (clazz == Properties.class) {
            serializeMap(Header.PROPERTIES, out, obj, objectStack);
        } else if (clazz == Fun.Tuple2.class){
            out.write(Header.TUPLE2);
            Fun.Tuple2 t = (Fun.Tuple2) obj;
            serialize(out, t.a, objectStack);
            serialize(out, t.b, objectStack);
        } else if (clazz == Fun.Tuple3.class){
            out.write(Header.TUPLE3);
            Fun.Tuple3 t = (Fun.Tuple3) obj;
            serialize(out, t.a, objectStack);
            serialize(out, t.b, objectStack);
            serialize(out, t.c, objectStack);
        } else if (clazz == Fun.Tuple4.class){
            out.write(Header.TUPLE4);
            Fun.Tuple4 t = (Fun.Tuple4) obj;
            serialize(out, t.a, objectStack);
            serialize(out, t.b, objectStack);
            serialize(out, t.c, objectStack);
            serialize(out, t.d, objectStack);
        } else if (clazz == BTreeKeySerializer.Tuple2KeySerializer.class){
            out.write(Header.MAPDB);
            Utils.packInt(out,HeaderMapDB.KEY_TUPLE2_SERIALIZER);
            BTreeKeySerializer.Tuple2KeySerializer s = (BTreeKeySerializer.Tuple2KeySerializer) obj;
            serialize(out, s.aComparator);
            serialize(out, s.aSerializer);
            serialize(out, s.bSerializer);
        } else if (clazz == BTreeKeySerializer.Tuple3KeySerializer.class){
            out.write(Header.MAPDB);
            Utils.packInt(out,HeaderMapDB.KEY_TUPLE3_SERIALIZER);
            BTreeKeySerializer.Tuple3KeySerializer s = (BTreeKeySerializer.Tuple3KeySerializer) obj;
            serialize(out, s.aComparator);
            serialize(out, s.bComparator);
            serialize(out, s.aSerializer);
            serialize(out, s.bSerializer);
            serialize(out, s.cSerializer);
        } else if (clazz == BTreeKeySerializer.Tuple4KeySerializer.class){
            out.write(Header.MAPDB);
            Utils.packInt(out,HeaderMapDB.KEY_TUPLE4_SERIALIZER);
            BTreeKeySerializer.Tuple4KeySerializer s = (BTreeKeySerializer.Tuple4KeySerializer) obj;
            serialize(out, s.aComparator);
            serialize(out, s.bComparator);
            serialize(out, s.cComparator);
            serialize(out, s.aSerializer);
            serialize(out, s.bSerializer);
            serialize(out, s.cSerializer);
            serialize(out, s.dSerializer);
        } else {
            serializeUnknownObject(out, obj, objectStack);
        }

    }


    protected void serializeClass(DataOutput out, Class clazz) throws IOException {
        //TODO override in SerializerPojo
        out.writeUTF(clazz.getName());
    }


    private void serializeMap(int header, DataOutput out, Object obj, FastArrayList<Object> objectStack) throws IOException {
        Map l = (Map) obj;
        out.write(header);
        Utils.packInt(out, l.size());
        for (Object o : l.keySet()) {
            serialize(out, o, objectStack);
            serialize(out, l.get(o), objectStack);
        }
    }

    private void serializeCollection(int header, DataOutput out, Object obj, FastArrayList<Object> objectStack) throws IOException {
        Collection l = (Collection) obj;
        out.write(header);
        Utils.packInt(out, l.size());

        for (Object o : l)
            serialize(out, o, objectStack);

    }

    private void serializeByteArray(DataOutput out, byte[] b) throws IOException {
        boolean allEqual = b.length>0;
        //check if all values in byte[] are equal
        for(int i=1;i<b.length;i++){
            if(b[i-1]!=b[i]){
                allEqual=false;
                break;
            }
        }
        if(allEqual){
            out.write(Header.ARRAY_BYTE_ALL_EQUAL);
            Utils.packInt(out, b.length);
            out.write(b[0]);
        }else{
            out.write(Header.ARRAY_BYTE);
            Utils.packInt(out, b.length);
            out.write(b);
        }
    }


    static String deserializeString(DataInput buf, int len) throws IOException {
        char[] b = new char[len];
        for (int i = 0; i < len; i++)
            b[i] = (char) Utils.unpackInt(buf);

        return new String(b);
    }


    @Override
    public Object deserialize(DataInput is, int capacity) throws IOException {
        if(capacity==0) return null;
        return deserialize(is, null);
    }

    public Object deserialize(DataInput is, FastArrayList<Object> objectStack) throws IOException {

        Object ret = null;

        final int head = is.readUnsignedByte();

        /** first try to deserialize object without allocating object stack*/
        switch (head) {
            case Header.ZERO_FAIL:
                throw new IOError(new IOException("Zero Header, data corrupted"));
            case Header.NULL:
                break;
            case Header.BOOLEAN_TRUE:
                ret = Boolean.TRUE;
                break;
            case Header.BOOLEAN_FALSE:
                ret = Boolean.FALSE;
                break;
            case Header.INT_M9:
            case Header.INT_M8:
            case Header.INT_M7:
            case Header.INT_M6:
            case Header.INT_M5:
            case Header.INT_M4:
            case Header.INT_M3:
            case Header.INT_M2:
            case Header.INT_M1:
            case Header.INT_0:
            case Header.INT_1:
            case Header.INT_2:
            case Header.INT_3:
            case Header.INT_4:
            case Header.INT_5:
            case Header.INT_6:
            case Header.INT_7:
            case Header.INT_8:
            case Header.INT_9:
            case Header.INT_10:
            case Header.INT_11:
            case Header.INT_12:
            case Header.INT_13:
            case Header.INT_14:
            case Header.INT_15:
            case Header.INT_16:
                ret = Integer.valueOf(head-Header.INT_M9-9);
                break;
            case Header.INT_MIN_VALUE:
                ret = Integer.valueOf(Integer.MIN_VALUE);
                break;
            case Header.INT_MAX_VALUE:
                ret = Integer.valueOf(Integer.MAX_VALUE);
                break;
            case Header.INT_F1:
                ret = Integer.valueOf(is.readUnsignedByte()&0xFF);
                break;
            case Header.INT_MF1:
                ret = Integer.valueOf(-(is.readUnsignedByte()&0xFF));
                break;
            case Header.INT_F2:
                ret = Integer.valueOf(((is.readUnsignedByte()&0xFF) | ((is.readUnsignedByte()&0xFF)<<8)));
                break;
            case Header.INT_MF2:
                ret = Integer.valueOf(-((is.readUnsignedByte()&0xFF) | ((is.readUnsignedByte()&0xFF)<<8)));
                break;
            case Header.INT_F3:
                ret = Integer.valueOf(((is.readUnsignedByte()&0xFF) | ((is.readUnsignedByte()&0xFF)<<8) | ((is.readUnsignedByte()&0xFF)<<16)));
                break;
            case Header.INT_MF3:
                ret = Integer.valueOf(-((is.readUnsignedByte()&0xFF) | ((is.readUnsignedByte()&0xFF)<<8) | ((is.readUnsignedByte()&0xFF)<<16)));
                break;
            case Header.INT:
                ret = Integer.valueOf(is.readInt());
                break;

            case Header.LONG_M9:
            case Header.LONG_M8:
            case Header.LONG_M7:
            case Header.LONG_M6:
            case Header.LONG_M5:
            case Header.LONG_M4:
            case Header.LONG_M3:
            case Header.LONG_M2:
            case Header.LONG_M1:
            case Header.LONG_0:
            case Header.LONG_1:
            case Header.LONG_2:
            case Header.LONG_3:
            case Header.LONG_4:
            case Header.LONG_5:
            case Header.LONG_6:
            case Header.LONG_7:
            case Header.LONG_8:
            case Header.LONG_9:
            case Header.LONG_10:
            case Header.LONG_11:
            case Header.LONG_12:
            case Header.LONG_13:
            case Header.LONG_14:
            case Header.LONG_15:
            case Header.LONG_16:
                ret = Long.valueOf(head-Header.LONG_M9-9);
                break;
            case Header.LONG_MIN_VALUE:
                ret = Long.valueOf(Long.MIN_VALUE);
                break;
            case Header.LONG_MAX_VALUE:
                ret = Long.valueOf(Long.MAX_VALUE);
                break;
            case Header.LONG_F1:
                ret = Long.valueOf(is.readUnsignedByte()&0xFFL);
                break;
            case Header.LONG_MF1:
                ret = Long.valueOf(-(is.readUnsignedByte()&0xFFL));
                break;
            case Header.LONG_F2:
                ret = Long.valueOf(((is.readUnsignedByte()&0xFFL) | ((is.readUnsignedByte()&0xFFL)<<8)));
                break;
            case Header.LONG_MF2:
                ret = Long.valueOf(-((is.readUnsignedByte()&0xFFL) | ((is.readUnsignedByte()&0xFFL)<<8)));
                break;
            case Header.LONG_F3:
                ret = Long.valueOf(((is.readUnsignedByte()&0xFFL) | ((is.readUnsignedByte()&0xFFL)<<8) | ((is.readUnsignedByte()&0xFFL)<<16)));
                break;
            case Header.LONG_MF3:
                ret = Long.valueOf(-((is.readUnsignedByte()&0xFFL) | ((is.readUnsignedByte()&0xFFL)<<8) | ((is.readUnsignedByte()&0xFFL)<<16)));
                break;
            case Header.LONG_F4:
                ret = Long.valueOf(((is.readUnsignedByte()&0xFFL) | ((is.readUnsignedByte()&0xFFL)<<8) | ((is.readUnsignedByte()&0xFFL)<<16)
                        | ((is.readUnsignedByte()&0xFFL)<<24)  ));
                break;
            case Header.LONG_MF4:
                ret = Long.valueOf(-((is.readUnsignedByte()&0xFFL) | ((is.readUnsignedByte()&0xFFL)<<8) | ((is.readUnsignedByte()&0xFFL)<<16)
                        | ((is.readUnsignedByte()&0xFFL)<<24)  ));
                break;
            case Header.LONG_F5:
                ret = Long.valueOf(((is.readUnsignedByte()&0xFFL) | ((is.readUnsignedByte()&0xFFL)<<8) | ((is.readUnsignedByte()&0xFFL)<<16)
                        | ((is.readUnsignedByte()&0xFFL)<<24) | ((is.readUnsignedByte()&0xFFL)<<32) ));
                break;
            case Header.LONG_MF5:
                ret = Long.valueOf(-((is.readUnsignedByte()&0xFFL) | ((is.readUnsignedByte()&0xFFL)<<8) | ((is.readUnsignedByte()&0xFFL)<<16)
                        | ((is.readUnsignedByte()&0xFFL)<<24) | ((is.readUnsignedByte()&0xFFL)<<32) ));
                break;
            case Header.LONG_F6:
                ret = Long.valueOf(((is.readUnsignedByte()&0xFFL) | ((is.readUnsignedByte()&0xFFL)<<8) | ((is.readUnsignedByte()&0xFFL)<<16)
                        | ((is.readUnsignedByte()&0xFFL)<<24) | ((is.readUnsignedByte()&0xFFL)<<32)  | ((is.readUnsignedByte()&0xFFL)<<40) ));
                break;
            case Header.LONG_MF6:
                ret = Long.valueOf(-((is.readUnsignedByte()&0xFFL) | ((is.readUnsignedByte()&0xFFL)<<8) | ((is.readUnsignedByte()&0xFFL)<<16)
                        | ((is.readUnsignedByte()&0xFFL)<<24) | ((is.readUnsignedByte()&0xFFL)<<32) | ((is.readUnsignedByte()&0xFFL)<<40)));
                break;
            case Header.LONG_F7:
                ret = Long.valueOf(((is.readUnsignedByte()&0xFFL) | ((is.readUnsignedByte()&0xFFL)<<8) | ((is.readUnsignedByte()&0xFFL)<<16)
                        | ((is.readUnsignedByte()&0xFFL)<<24) | ((is.readUnsignedByte()&0xFFL)<<32)  | ((is.readUnsignedByte()&0xFFL)<<40)
                        | ((is.readUnsignedByte()&0xFFL)<<48) ));
                break;
            case Header.LONG_MF7:
                ret = Long.valueOf(-((is.readUnsignedByte()&0xFFL) | ((is.readUnsignedByte()&0xFFL)<<8) | ((is.readUnsignedByte()&0xFFL)<<16)
                        | ((is.readUnsignedByte()&0xFFL)<<24) | ((is.readUnsignedByte()&0xFFL)<<32) | ((is.readUnsignedByte()&0xFFL)<<40)
                        | ((is.readUnsignedByte()&0xFFL)<<48) ));
                break;
            case Header.LONG:
                ret = Long.valueOf(is.readLong());
                break;

            case Header.BYTE_M1:
                ret = Byte.valueOf((byte)-1);
                break;
            case Header.BYTE_0:
                ret = Byte.valueOf((byte) 0);
                break;
            case Header.BYTE_1:
                ret = Byte.valueOf((byte) 1);
                break;
            case Header.BYTE:
                ret = is.readByte();
                break;

            case Header.CHAR_0:
                ret = Character.valueOf((char) 0);
                break;
            case Header.CHAR_1:
                ret = Character.valueOf((char) 1);
                break;
            case Header.CHAR_255:
                ret = Character.valueOf((char) is.readUnsignedByte());
                break;
            case Header.CHAR:
                ret = is.readChar();
                break;


            case Header.SHORT_M1:
                ret = Short.valueOf((short)-1);
                break;
            case Header.SHORT_0:
                ret = Short.valueOf((short)0);
                break;
            case Header.SHORT_1:
                ret = Short.valueOf((short)1);
                break;
            case Header.SHORT_255:
                ret = Short.valueOf((short) is.readUnsignedByte());
                break;
            case Header.SHORT_M255:
                ret = Short.valueOf(((short) -is.readUnsignedByte()));
                break;
            case Header.SHORT:
                ret = Short.valueOf(is.readShort());
                break;

            case Header.FLOAT_M1:
                ret = (float) -1;
                break;
            case Header.FLOAT_0:
                ret = (float) 0;
                break;
            case Header.FLOAT_1:
                ret = (float) 1;
                break;
            case Header.FLOAT_255:
                ret = (float) is.readUnsignedByte();
                break;
            case Header.FLOAT_SHORT:
                ret = (float) is.readShort();
                break;
            case Header.FLOAT:
                ret = is.readFloat();
                break;
            case Header.DOUBLE_M1:
                ret = -1D;
                break;
            case Header.DOUBLE_0:
                ret = 0D;
                break;
            case Header.DOUBLE_1:
                ret = 1D;
                break;
            case Header.DOUBLE_255:
                ret = (double) is.readUnsignedByte();
                break;
            case Header.DOUBLE_SHORT:
                ret = (double) is.readShort();
                break;
            case Header.DOUBLE_INT:
                ret = (double) is.readInt();
                break;
            case Header.DOUBLE:
                ret = is.readDouble();
                break;

            case Header.ARRAY_BYTE_ALL_EQUAL:
                byte[] b = new byte[Utils.unpackInt(is)];
                Arrays.fill(b, is.readByte());
                ret = b;
                break;
            case Header.ARRAY_BYTE:
                ret = deserializeArrayByte(is);
                break;

            case Header.ARRAY_BOOLEAN:
                ret = readBooleanArray(is);
                break;
            case Header.ARRAY_SHORT:
                int size = Utils.unpackInt(is);
                ret = new short[size];
                for(int i=0;i<size;i++) ((short[])ret)[i] = is.readShort();
                break;
            case Header.ARRAY_DOUBLE:
                size = Utils.unpackInt(is);
                ret = new double[size];
                for(int i=0;i<size;i++) ((double[])ret)[i] = is.readDouble();
                break;
            case Header.ARRAY_FLOAT:
                size = Utils.unpackInt(is);
                ret = new float[size];
                for(int i=0;i<size;i++) ((float[])ret)[i] = is.readFloat();
                break;
            case Header.ARRAY_CHAR:
                size = Utils.unpackInt(is);
                ret = new char[size];
                for(int i=0;i<size;i++) ((char[])ret)[i] = is.readChar();
                break;

            case Header.ARRAY_INT_BYTE:
                size = Utils.unpackInt(is);
                ret=new int[size];
                for(int i=0;i<size;i++) ((int[])ret)[i] = is.readByte();
                break;
            case Header.ARRAY_INT_SHORT:
                size = Utils.unpackInt(is);
                ret=new int[size];
                for(int i=0;i<size;i++) ((int[])ret)[i] = is.readShort();
                break;
            case Header.ARRAY_INT_PACKED:
                size = Utils.unpackInt(is);
                ret=new int[size];
                for(int i=0;i<size;i++) ((int[])ret)[i] = Utils.unpackInt(is);
                break;
            case Header.ARRAY_INT:
                size = Utils.unpackInt(is);
                ret=new int[size];
                for(int i=0;i<size;i++) ((int[])ret)[i] = is.readInt();
                break;

            case Header.ARRAY_LONG_BYTE:
                size = Utils.unpackInt(is);
                ret=new long[size];
                for(int i=0;i<size;i++) ((long[])ret)[i] = is.readByte();
                break;
            case Header.ARRAY_LONG_SHORT:
                size = Utils.unpackInt(is);
                ret=new long[size];
                for(int i=0;i<size;i++) ((long[])ret)[i] = is.readShort();
                break;
            case Header.ARRAY_LONG_PACKED:
                size = Utils.unpackInt(is);
                ret=new long[size];
                for(int i=0;i<size;i++) ((long[])ret)[i] = Utils.unpackLong(is);
                break;
            case Header.ARRAY_LONG_INT:
                size = Utils.unpackInt(is);
                ret=new long[size];
                for(int i=0;i<size;i++) ((long[])ret)[i] = is.readInt();
                break;
            case Header.ARRAY_LONG:
                size = Utils.unpackInt(is);
                ret=new long[size];
                for(int i=0;i<size;i++) ((long[])ret)[i] = is.readLong();
                break;

            case Header.STRING:
                ret = deserializeString(is, Utils.unpackInt(is));
                break;
            case Header.STRING_0:
                ret = Utils.EMPTY_STRING;
                break;
            case Header.STRING_1:
            case Header.STRING_2:
            case Header.STRING_3:
            case Header.STRING_4:
            case Header.STRING_5:
            case Header.STRING_6:
            case Header.STRING_7:
            case Header.STRING_8:
            case Header.STRING_9:
            case Header.STRING_10:
                ret = deserializeString(is, head-Header.STRING_0);
                break;

            case Header.BIGINTEGER:
                ret = new BigInteger(deserializeArrayByte(is));
                break;
            case Header.BIGDECIMAL:
                ret = new BigDecimal(new BigInteger(deserializeArrayByte(is)), Utils.unpackInt(is));
                break;

            case Header.CLASS:
                ret = deserializeClass(is);
                break;
            case Header.DATE:
                ret = new Date(is.readLong());
                break;
            case Header.UUID:
                ret = new UUID(is.readLong(), is.readLong());
                break;

            case Header.MAPDB:
                ret = deserializeMapDB(is,objectStack);
                break;

            case Header.ARRAYLIST_PACKED_LONG:
                ret = deserializeArrayListPackedLong(is);
                break;

            case Header.TUPLE2:
                ret = new Fun.Tuple2(deserialize(is, objectStack), deserialize(is, objectStack));
                break;
            case Header.TUPLE3:
                ret = new Fun.Tuple3(deserialize(is, objectStack), deserialize(is, objectStack), deserialize(is, objectStack));
                break;
            case Header.TUPLE4:
                ret = new Fun.Tuple4(deserialize(is, objectStack), deserialize(is, objectStack), deserialize(is, objectStack), deserialize(is, objectStack));
                break;
            case Header.FUN_HI:
                ret = Fun.HI;
                break;
            case Header.JAVA_SERIALIZATION:
                throw new InternalError("Wrong header, data were probably serialized with java.lang.ObjectOutputStream, not with MapDB serialization");
            case Header.ARRAY_OBJECT_PACKED_LONG:
                ret = deserializeArrayObjectPackedLong(is);
                break;
            case Header.ARRAY_OBJECT_ALL_NULL:
                ret = deserializeArrayObjectAllNull(is);
                break;
            case Header.ARRAY_OBJECT_NO_REFS:
                ret = deserializeArrayObjectNoRefs(is);
                break;

            case -1:
                throw new EOFException();

        }

        if (ret != null || head == Header.NULL) {
            if (objectStack != null)
                objectStack.add(ret);
            return ret;
        }

        /**  something else which needs object stack initialized*/

        if (objectStack == null)
            objectStack = new FastArrayList();
        int oldObjectStackSize = objectStack.size;

        switch (head) {
            case Header.OBJECT_STACK:
                ret = objectStack.data[Utils.unpackInt(is)];
                break;
            case Header.ARRAYLIST:
                ret = deserializeArrayList(is, objectStack);
                break;
            case Header.ARRAY_OBJECT:
                ret = deserializeArrayObject(is, objectStack);
                break;
            case Header.LINKEDLIST:
                ret = deserializeLinkedList(is, objectStack);
                break;
            case Header.TREESET:
                ret = deserializeTreeSet(is, objectStack);
                break;
            case Header.HASHSET:
                ret = deserializeHashSet(is, objectStack);
                break;
            case Header.LINKEDHASHSET:
                ret = deserializeLinkedHashSet(is, objectStack);
                break;
            case Header.TREEMAP:
                ret = deserializeTreeMap(is, objectStack);
                break;
            case Header.HASHMAP:
                ret = deserializeHashMap(is, objectStack);
                break;
            case Header.LINKEDHASHMAP:
                ret = deserializeLinkedHashMap(is, objectStack);
                break;
            case Header.PROPERTIES:
                ret = deserializeProperties(is, objectStack);
                break;
            default:
                ret = deserializeUnknownHeader(is, head, objectStack);
                break;
        }

        if (head != Header.OBJECT_STACK && objectStack.size == oldObjectStackSize) {
            //check if object was not already added to stack as part of collection
            objectStack.add(ret);
        }


        return ret;
    }

    protected interface HeaderMapDB{
        int B_TREE_SERIALIZER_POS_LONG = 1;
        int B_TREE_SERIALIZER_STRING = 2;
        int B_TREE_SERIALIZER_POS_INT = 3;
        int LONG_SERIALIZER = 4;
        int INT_SERIALIZER = 5;
        int EMPTY_SERIALIZER = 6;

        int KEY_TUPLE2_SERIALIZER = 7;
        int KEY_TUPLE3_SERIALIZER = 8;
        int KEY_TUPLE4_SERIALIZER = 9;
        int COMPARABLE_COMPARATOR_WITH_NULLS = 10;
        int COMPARABLE_COMPARATOR = 11;
        int THIS_SERIALIZER = 12;
        int BASIC_SERIALIZER = 13;
        int STRING_SERIALIZER = 14;
        int B_TREE_BASIC_KEY_SERIALIZER = 15;
        int BOOLEAN_SERIALIZER = 16;
        int SERIALIZER_BYTE_ARRAY_NOSIZE = 17;
    }


    protected Object deserializeMapDB(DataInput is, FastArrayList<Object> objectStack) throws IOException {
        int head = Utils.unpackInt(is);
        switch(head){



            case HeaderMapDB.B_TREE_SERIALIZER_POS_LONG:
                return  BTreeKeySerializer.ZERO_OR_POSITIVE_LONG;
            case HeaderMapDB.B_TREE_SERIALIZER_POS_INT:
                return BTreeKeySerializer.ZERO_OR_POSITIVE_INT;
            case HeaderMapDB.B_TREE_SERIALIZER_STRING:
                return BTreeKeySerializer.STRING;

            case HeaderMapDB.LONG_SERIALIZER:
                return Serializer.LONG;
            case HeaderMapDB.INT_SERIALIZER:
                return Serializer.INTEGER;
            case HeaderMapDB.EMPTY_SERIALIZER:
                return Serializer.EMPTY_SERIALIZER;
            case HeaderMapDB.BOOLEAN_SERIALIZER:
                return Serializer.BOOLEAN;
            case HeaderMapDB.SERIALIZER_BYTE_ARRAY_NOSIZE:
                return Serializer.BYTE_ARRAY_NOSIZE;


            case HeaderMapDB.COMPARABLE_COMPARATOR: return Utils.COMPARABLE_COMPARATOR;


            case HeaderMapDB.KEY_TUPLE2_SERIALIZER:
                return new BTreeKeySerializer.Tuple2KeySerializer(
                        (Comparator)deserialize(is,objectStack), (Serializer)deserialize(is,objectStack), (Serializer)deserialize(is,objectStack));

            case HeaderMapDB.KEY_TUPLE3_SERIALIZER:
                return new BTreeKeySerializer.Tuple3KeySerializer(
                        (Comparator)deserialize(is,objectStack),(Comparator)deserialize(is,objectStack),
                        (Serializer)deserialize(is,objectStack),(Serializer)deserialize(is,objectStack), (Serializer)deserialize(is,objectStack));

            case HeaderMapDB.KEY_TUPLE4_SERIALIZER:
                return new BTreeKeySerializer.Tuple4KeySerializer(
                        (Comparator)deserialize(is,objectStack),(Comparator)deserialize(is,objectStack),(Comparator)deserialize(is,objectStack),
                        (Serializer)deserialize(is,objectStack), (Serializer)deserialize(is,objectStack),
                        (Serializer)deserialize(is,objectStack), (Serializer)deserialize(is,objectStack));
            case HeaderMapDB.COMPARABLE_COMPARATOR_WITH_NULLS:
                return Utils.COMPARABLE_COMPARATOR_WITH_NULLS;

            case HeaderMapDB.B_TREE_BASIC_KEY_SERIALIZER:
                return new BTreeKeySerializer.BasicKeySerializer(this);
            case HeaderMapDB.THIS_SERIALIZER:
                return this;
            case HeaderMapDB.BASIC_SERIALIZER:
                return BASIC;
            case HeaderMapDB.STRING_SERIALIZER:
                return Serializer.STRING_NOSIZE;

            default:
                throw new IOError(new IOException("Unknown header byte, data corrupted"));
        }
    }


    protected  Class deserializeClass(DataInput is) throws IOException {
        //TODO override 'deserializeClass' in SerializerPojo
        try {
            return Class.forName(is.readUTF());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }


    private byte[] deserializeArrayByte(DataInput is) throws IOException {
        byte[] bb = new byte[Utils.unpackInt(is)];
        is.readFully(bb);
        return bb;
    }




    private Object[] deserializeArrayObject(DataInput is, FastArrayList<Object> objectStack) throws IOException {
        int size = Utils.unpackInt(is);
        Class clazz = deserializeClass(is);
        Object[] s = (Object[]) Array.newInstance(clazz, size);
        objectStack.add(s);
        for (int i = 0; i < size; i++){
            s[i] = deserialize(is, objectStack);
        }
        return s;
    }

    private Object[] deserializeArrayObjectNoRefs(DataInput is) throws IOException {
        int size = Utils.unpackInt(is);
        Class clazz = deserializeClass(is);
        Object[] s = (Object[]) Array.newInstance(clazz, size);
        for (int i = 0; i < size; i++){
            s[i] = deserialize(is, null);
        }
        return s;
    }


    private Object[] deserializeArrayObjectAllNull(DataInput is) throws IOException {
        int size = Utils.unpackInt(is);
        Class clazz = deserializeClass(is);
        Object[] s = (Object[]) Array.newInstance(clazz, size);
        return s;
    }


    private Object[] deserializeArrayObjectPackedLong(DataInput is) throws IOException {
        int size = is.readUnsignedByte();
        Object[] s = new Object[size];
        for (int i = 0; i < size; i++) {
            long l = Utils.unpackLong(is);
            if (l == 0)
                s[i] = null;
            else
                s[i] = l - 1;
        }
        return s;
    }


    private ArrayList<Object> deserializeArrayList(DataInput is, FastArrayList<Object> objectStack) throws IOException {
        int size = Utils.unpackInt(is);
        ArrayList<Object> s = new ArrayList<Object>(size);
        objectStack.add(s);
        for (int i = 0; i < size; i++) {
            s.add(deserialize(is, objectStack));
        }
        return s;
    }

    private ArrayList<Object> deserializeArrayListPackedLong(DataInput is) throws IOException {
        int size = is.readUnsignedByte();
        if (size < 0)
            throw new EOFException();

        ArrayList<Object> s = new ArrayList<Object>(size);
        for (int i = 0; i < size; i++) {
            long l = Utils.unpackLong(is);
            if (l == 0)
                s.add(null);
            else
                s.add(l - 1);
        }
        return s;
    }


    private java.util.LinkedList deserializeLinkedList(DataInput is, FastArrayList<Object> objectStack) throws IOException {
        int size = Utils.unpackInt(is);
        java.util.LinkedList s = new java.util.LinkedList();
        objectStack.add(s);
        for (int i = 0; i < size; i++)
            s.add(deserialize(is, objectStack));
        return s;
    }




    private HashSet<Object> deserializeHashSet(DataInput is, FastArrayList<Object> objectStack) throws IOException {
        int size = Utils.unpackInt(is);
        HashSet<Object> s = new HashSet<Object>(size);
        objectStack.add(s);
        for (int i = 0; i < size; i++)
            s.add(deserialize(is, objectStack));
        return s;
    }


    private LinkedHashSet<Object> deserializeLinkedHashSet(DataInput is, FastArrayList<Object> objectStack) throws IOException {
        int size = Utils.unpackInt(is);
        LinkedHashSet<Object> s = new LinkedHashSet<Object>(size);
        objectStack.add(s);
        for (int i = 0; i < size; i++)
            s.add(deserialize(is, objectStack));
        return s;
    }


    private TreeSet<Object> deserializeTreeSet(DataInput is, FastArrayList<Object> objectStack) throws IOException {
        int size = Utils.unpackInt(is);
        TreeSet<Object> s = new TreeSet<Object>();
        objectStack.add(s);
        Comparator comparator = (Comparator) deserialize(is, objectStack);
        if (comparator != null)
            s = new TreeSet<Object>(comparator);

        for (int i = 0; i < size; i++)
            s.add(deserialize(is, objectStack));
        return s;
    }


    private TreeMap<Object, Object> deserializeTreeMap(DataInput is, FastArrayList<Object> objectStack) throws IOException {
        int size = Utils.unpackInt(is);

        TreeMap<Object, Object> s = new TreeMap<Object, Object>();
        objectStack.add(s);
        Comparator comparator = (Comparator) deserialize(is, objectStack);
        if (comparator != null)
            s = new TreeMap<Object, Object>(comparator);
        for (int i = 0; i < size; i++)
            s.put(deserialize(is, objectStack), deserialize(is, objectStack));
        return s;
    }


    private HashMap<Object, Object> deserializeHashMap(DataInput is, FastArrayList<Object> objectStack) throws IOException {
        int size = Utils.unpackInt(is);

        HashMap<Object, Object> s = new HashMap<Object, Object>(size);
        objectStack.add(s);
        for (int i = 0; i < size; i++)
            s.put(deserialize(is, objectStack), deserialize(is, objectStack));
        return s;
    }


    private LinkedHashMap<Object, Object> deserializeLinkedHashMap(DataInput is, FastArrayList<Object> objectStack) throws IOException {
        int size = Utils.unpackInt(is);

        LinkedHashMap<Object, Object> s = new LinkedHashMap<Object, Object>(size);
        objectStack.add(s);
        for (int i = 0; i < size; i++)
            s.put(deserialize(is, objectStack), deserialize(is, objectStack));
        return s;
    }



    private Properties deserializeProperties(DataInput is, FastArrayList<Object> objectStack) throws IOException {
        int size = Utils.unpackInt(is);

        Properties s = new Properties();
        objectStack.add(s);
        for (int i = 0; i < size; i++)
            s.put(deserialize(is, objectStack), deserialize(is, objectStack));
        return s;
    }

    /** override this method to extend SerializerBase functionality*/
    protected void serializeUnknownObject(DataOutput out, Object obj, FastArrayList<Object> objectStack) throws IOException {
        throw new InternalError("Could not serialize unknown object: "+obj.getClass().getName());
    }
    /** override this method to extend SerializerBase functionality*/
    protected Object deserializeUnknownHeader(DataInput is, int head, FastArrayList<Object> objectStack) throws IOException {
        throw new InternalError("Unknown serialization header: " + head);
    }

    /**
     * Builds a byte array from the array of booleans, compressing up to 8 booleans per byte.
     *
     * @param bool The booleans to be compressed.
     * @return The fully compressed byte array.
     */
    protected static byte[] booleanToByteArray(boolean[] bool) {
        int boolLen = bool.length;
        int mod8 = boolLen%8;
        byte[] boolBytes = new byte[(boolLen/8)+((boolLen%8 == 0)?0:1)];

        boolean isFlushWith8 = mod8 == 0;
        int length = (isFlushWith8)?boolBytes.length:boolBytes.length-1;
        int x = 0;
        int boolByteIndex;
        for (boolByteIndex=0; boolByteIndex<length;) {
            byte b = (byte)	(((bool[x++]? 0x01 : 0x00) << 0) |
                    ((bool[x++]? 0x01 : 0x00) << 1) |
                    ((bool[x++]? 0x01 : 0x00) << 2) |
                    ((bool[x++]? 0x01 : 0x00) << 3) |
                    ((bool[x++]? 0x01 : 0x00) << 4) |
                    ((bool[x++]? 0x01 : 0x00) << 5) |
                    ((bool[x++]? 0x01 : 0x00) << 6) |
                    ((bool[x++]? 0x01 : 0x00) << 7));
            boolBytes[boolByteIndex++] = b;
        }
        if (!isFlushWith8) {//If length is not a multiple of 8 we must do the last byte conditionally on every element.
            byte b = (byte)	0x00;

            switch(mod8) {
                case 1:
                    b |=	((bool[x++]? 0x01 : 0x00) << 0);
                    break;
                case 2:
                    b |=	((bool[x++]? 0x01 : 0x00) << 0) |
                            ((bool[x++]? 0x01 : 0x00) << 1);
                    break;
                case 3:
                    b |=	((bool[x++]? 0x01 : 0x00) << 0) |
                            ((bool[x++]? 0x01 : 0x00) << 1) |
                            ((bool[x++]? 0x01 : 0x00) << 2);
                    break;
                case 4:
                    b |=	((bool[x++]? 0x01 : 0x00) << 0) |
                            ((bool[x++]? 0x01 : 0x00) << 1) |
                            ((bool[x++]? 0x01 : 0x00) << 2) |
                            ((bool[x++]? 0x01 : 0x00) << 3);
                    break;
                case 5:
                    b |=	((bool[x++]? 0x01 : 0x00) << 0) |
                            ((bool[x++]? 0x01 : 0x00) << 1) |
                            ((bool[x++]? 0x01 : 0x00) << 2) |
                            ((bool[x++]? 0x01 : 0x00) << 3) |
                            ((bool[x++]? 0x01 : 0x00) << 4);
                    break;
                case 6:
                    b |=	((bool[x++]? 0x01 : 0x00) << 0) |
                            ((bool[x++]? 0x01 : 0x00) << 1) |
                            ((bool[x++]? 0x01 : 0x00) << 2) |
                            ((bool[x++]? 0x01 : 0x00) << 3) |
                            ((bool[x++]? 0x01 : 0x00) << 4) |
                            ((bool[x++]? 0x01 : 0x00) << 5);
                    break;
                case 7:
                    b |=	((bool[x++]? 0x01 : 0x00) << 0) |
                            ((bool[x++]? 0x01 : 0x00) << 1) |
                            ((bool[x++]? 0x01 : 0x00) << 2) |
                            ((bool[x++]? 0x01 : 0x00) << 3) |
                            ((bool[x++]? 0x01 : 0x00) << 4) |
                            ((bool[x++]? 0x01 : 0x00) << 5) |
                            ((bool[x++]? 0x01 : 0x00) << 6);
                    break;
                case 8:
                    b |=	((bool[x++]? 0x01 : 0x00) << 0) |
                            ((bool[x++]? 0x01 : 0x00) << 1) |
                            ((bool[x++]? 0x01 : 0x00) << 2) |
                            ((bool[x++]? 0x01 : 0x00) << 3) |
                            ((bool[x++]? 0x01 : 0x00) << 4) |
                            ((bool[x++]? 0x01 : 0x00) << 5) |
                            ((bool[x++]? 0x01 : 0x00) << 6) |
                            ((bool[x++]? 0x01 : 0x00) << 7);
                    break;
            }

            ////////////////////////
            // OLD
            ////////////////////////
			/* The code below was replaced with the switch statement
			 * above. It increases performance by only doing 1
			 * check against mod8 (switch statement) and only doing 1
			 * assignment operation for every possible value of mod8,
			 * rather than doing up to 8 assignment operations and an
			 * if check in between each one. The code is longer but
			 * faster.
			 *
			byte b = (byte)	0x00;
			b |= ((bool[x++]? 0x01 : 0x00) << 0);
			if (mod8 > 1) {
				b |= ((bool[x++]? 0x01 : 0x00) << 1);
				if (mod8 > 2) {
					b |= ((bool[x++]? 0x01 : 0x00) << 2);
					if (mod8 > 3) {
						b |= ((bool[x++]? 0x01 : 0x00) << 3);
						if (mod8 > 4) {
							b |= ((bool[x++]? 0x01 : 0x00) << 4);
							if (mod8 > 5) {
								b |= ((bool[x++]? 0x01 : 0x00) << 5);
								if (mod8 > 6) {
									b |= ((bool[x++]? 0x01 : 0x00) << 6);
									if (mod8 > 7) {
										b |= ((bool[x++]? 0x01 : 0x00) << 7);
									}
								}
							}
						}
					}
				}
			}
			*/
            boolBytes[boolByteIndex++] = b;
        }

        return boolBytes;
    }



    /**
     * Unpacks an integer from the DataInput indicating the number of booleans that are compressed. It then calculates
     * the number of bytes, reads them in, and decompresses and converts them into an array of booleans using the
     * toBooleanArray(byte[]); method. The array of booleans are trimmed to <code>numBools</code> elements. This is
     * necessary in situations where the number of booleans is not a multiple of 8.
     *
     * @return The boolean array decompressed from the bytes read in.
     * @throws IOException If an error occurred while reading.
     */
    protected static boolean[] readBooleanArray(DataInput is) throws IOException {
        int numBools = Utils.unpackInt(is);
        int length = (numBools/8)+((numBools%8 == 0)?0:1);
        byte[] boolBytes = new byte[length];
        is.readFully(boolBytes);


        boolean[] tmp = new boolean[boolBytes.length*8];
        int len = boolBytes.length;
        int boolIndex = 0;
        for (int x=0; x<len; x++) {
            for (int y=0; y<8; y++) {
                tmp[boolIndex++] = (boolBytes[x] & (0x01 << y)) != 0x00;
            }
        }

        //Trim excess booleans
        boolean[] finalBoolArray = new boolean[numBools];
        System.arraycopy(tmp, 0, finalBoolArray, 0, numBools);

        //Return the trimmed, uncompressed boolean array
        return finalBoolArray;
    }





    /**
     * Header byte, is used at start of each record to indicate data type
     * WARNING !!! values bellow must be unique !!!!!
     *
     * @author Jan Kotek
     */
    protected interface Header {

        int ZERO_FAIL=0; //zero is invalid value, so it fails with uninitialized values
        int NULL = 1;
        int BOOLEAN_TRUE = 2;
        int BOOLEAN_FALSE = 3;

        int INT_M9 = 4;
        int INT_M8 = 5;
        int INT_M7 = 6;
        int INT_M6 = 7;
        int INT_M5 = 8;
        int INT_M4 = 9;
        int INT_M3 = 10;
        int INT_M2 = 11;
        int INT_M1 = 12;
        int INT_0 = 13;
        int INT_1 = 14;
        int INT_2 = 15;
        int INT_3 = 16;
        int INT_4 = 17;
        int INT_5 = 18;
        int INT_6 = 19;
        int INT_7 = 20;
        int INT_8 = 21;
        int INT_9 = 22;
        int INT_10 = 23;
        int INT_11 = 24;
        int INT_12 = 25;
        int INT_13 = 26;
        int INT_14 = 27;
        int INT_15 = 28;
        int INT_16 = 29;
        int INT_MIN_VALUE = 30;
        int INT_MAX_VALUE = 31;
        int INT_MF1 = 32;
        int INT_F1 = 33;
        int INT_MF2 = 34;
        int INT_F2 = 35;
        int INT_MF3 = 36;
        int INT_F3 = 37;
        int INT = 38;

        int LONG_M9 = 39;
        int LONG_M8 = 40;
        int LONG_M7 = 41;
        int LONG_M6 = 42;
        int LONG_M5 = 43;
        int LONG_M4 = 44;
        int LONG_M3 = 45;
        int LONG_M2 = 46;
        int LONG_M1 = 47;
        int LONG_0 = 48;
        int LONG_1 = 49;
        int LONG_2 = 50;
        int LONG_3 = 51;
        int LONG_4 = 52;
        int LONG_5 = 53;
        int LONG_6 = 54;
        int LONG_7 = 55;
        int LONG_8 = 56;
        int LONG_9 = 57;
        int LONG_10 = 58;
        int LONG_11 = 59;
        int LONG_12 = 60;
        int LONG_13 = 61;
        int LONG_14 = 62;
        int LONG_15 = 63;
        int LONG_16 = 64;
        int LONG_MIN_VALUE = 65;
        int LONG_MAX_VALUE = 66;
        int LONG_MF1 = 67;
        int LONG_F1 = 68;
        int LONG_MF2 = 69;
        int LONG_F2 = 70;
        int LONG_MF3 = 71;
        int LONG_F3 = 72;
        int LONG_MF4 = 73;
        int LONG_F4 = 74;
        int LONG_MF5 = 75;
        int LONG_F5 = 76;
        int LONG_MF6 = 77;
        int LONG_F6 = 78;
        int LONG_MF7 = 79;
        int LONG_F7 = 80;
        int LONG = 81;

        int BYTE_M1 = 82;
        int BYTE_0 = 83;
        int BYTE_1 = 84;
        int BYTE = 85;

        int CHAR_0 = 86;
        int CHAR_1 = 87;
        int CHAR_255 = 88;
        int CHAR = 89;

        int SHORT_M1 =90;
        int SHORT_0 = 91;
        int SHORT_1 = 92;
        int SHORT_255 = 93;
        int SHORT_M255 = 94;
        int SHORT = 95;

        int FLOAT_M1 = 96;
        int FLOAT_0 = 97;
        int FLOAT_1 = 98;
        int FLOAT_255 = 99;
        int FLOAT_SHORT = 100;
        int FLOAT = 101;

        int DOUBLE_M1 = 102;
        int DOUBLE_0 = 103;
        int DOUBLE_1 = 104;
        int DOUBLE_255 = 105;
        int DOUBLE_SHORT = 106;
        int DOUBLE_INT = 107;
        int DOUBLE = 108;

        int ARRAY_BYTE = 109;
        int ARRAY_BYTE_ALL_EQUAL = 110;

        int ARRAY_BOOLEAN = 111;
        int ARRAY_SHORT = 112;
        int ARRAY_CHAR = 113;
        int ARRAY_FLOAT = 114;
        int ARRAY_DOUBLE = 115;

        int ARRAY_INT_BYTE = 116;
        int ARRAY_INT_SHORT = 117;
        int ARRAY_INT_PACKED = 118;
        int ARRAY_INT = 119;

        int ARRAY_LONG_BYTE = 120;
        int ARRAY_LONG_SHORT = 121;
        int ARRAY_LONG_PACKED = 122;
        int ARRAY_LONG_INT = 123;
        int ARRAY_LONG = 124;

        int STRING_0 = 125;
        int STRING_1 = 126;
        int STRING_2 = 127;
        int STRING_3 = 128;
        int STRING_4 = 129;
        int STRING_5 = 130;
        int STRING_6 = 131;
        int STRING_7 = 132;
        int STRING_8 = 133;
        int STRING_9 = 134;
        int STRING_10 = 135;
        int STRING = 136;

        int BIGDECIMAL = 137;
        int BIGINTEGER = 138;


        int CLASS = 139;
        int DATE = 140;
        int FUN_HI = 141;
        int UUID = 142;

        //144 to 149 reserved for other non recursive objects

        int MAPDB = 150;
        int TUPLE2 = 151;
        int TUPLE3 = 152;
        int TUPLE4 = 153;
        int TUPLE5 = 154; //reserved for Tuple5 if we will ever implement it
        int TUPLE6 = 155; //reserved for Tuple6 if we will ever implement it
        int TUPLE7 = 156; //reserved for Tuple7 if we will ever implement it
        int TUPLE8 = 157; //reserved for Tuple8 if we will ever implement it


        int  ARRAY_OBJECT = 158;
        //special cases for BTree values which stores references
        int ARRAY_OBJECT_PACKED_LONG = 159;
        int ARRAYLIST_PACKED_LONG = 160;
        int ARRAY_OBJECT_ALL_NULL = 161;
        int ARRAY_OBJECT_NO_REFS = 162;

        int  ARRAYLIST = 163;
        int  TREEMAP = 164;
        int  HASHMAP = 165;
        int  LINKEDHASHMAP = 166;
        int  TREESET = 167;
        int  HASHSET = 168;
        int  LINKEDHASHSET = 169;
        int  LINKEDLIST = 170;
        int  PROPERTIES = 171;

        /**
         * Value used in Java Serialization header. For this header we throw an exception because data might be corrupted
         */
        int JAVA_SERIALIZATION = 172;

        /**
         * Use POJO Serializer to get class structure and set its fields
         */
        int POJO = 173;
        /**
         * used for reference to already serialized object in object graph
         */
        int OBJECT_STACK = 174;

    }



}
