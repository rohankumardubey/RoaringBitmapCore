package me.lemire.roaringbitmap;

import java.util.Arrays;
import java.util.Iterator;

public class BitmapContainer implements Container, Cloneable {
	long[] bitmap = new long[(1 << 16) / 64]; //a max of 65535 integers
											  // with 1024 chunks of 64 bits each	
	int cardinality;

	
	public BitmapContainer() {
		this.cardinality = 0;
	}
	
	public static int toIntUnsigned(short x) {
		return x & 0xFFFF;
	}

	public BitmapContainer(ArrayContainer arrayContainer) {
		this.cardinality = arrayContainer.cardinality;
		for(int k = 0; k < arrayContainer.cardinality; ++k) {
			final short x = arrayContainer.content[k];
			bitmap[toIntUnsigned(x)/64] |= (1l << x % 64);
		}

	}

	@Override
	public boolean contains(short i) {
			return (bitmap[toIntUnsigned(i)/64] & (1l << (i % 64))) != 0;		
	}

	@Override
	public Container add(short i) {
		if (!contains(i)) {
			bitmap[toIntUnsigned(i)/64] |= (1l << (i % 64));
			++cardinality;
		}
		return this;
	}

	@Override
	public Container remove(short x) {
		if (contains(x)) {
			--cardinality;
			bitmap[x / 64] &= ~(1l << (x % 64));
			if (cardinality <= 1024)
				return new ArrayContainer(this);
		}
		return this;
	}

	// for(int i=bs.nextSetBit(0); i>=0; i=bs.nextSetBit(i+1)) { // operate on
	// index i here }
	public int nextSetBit(int i) {
		int x = i / 64;
		if(x>=bitmap.length) return -1;
		long w = bitmap[x];
		w >>>= (i % 64);
		if (w != 0) {
			return i + Long.numberOfTrailingZeros(w);
		}
		++x;
		for (; x < bitmap.length; ++x) {
			if (bitmap[x] != 0) {
				return x * 64 + Long.numberOfTrailingZeros(bitmap[x]);
			}
		}
		return -1;
	}

	public short nextUnsetBit(int i) {
		int x = i / 64;
		long w = ~bitmap[x];
		w >>>= (i % 64);
		if (w != 0) {
			return (short) (i + Long.numberOfTrailingZeros(w));
		}
		++x;
		for (; x < bitmap.length; ++x) {
			if (bitmap[x] != ~0) {
				return (short) (x * 64 + Long.numberOfTrailingZeros(~bitmap[x]));
			}
		}
		return -1;
	}

	public void clear() {
		cardinality = 0;
		Arrays.fill(bitmap, 0);
	}

	@Override
	public Iterator<Short> iterator() {
		return new Iterator<Short>() {
			int i = BitmapContainer.this.nextSetBit(0);

			@Override
			public boolean hasNext() {
				return i >= 0;
			}

			@Override
			public Short next() {
				short j = (short) i;
				i = BitmapContainer.this.nextSetBit(i + 1);
				return new Short(j);
			}

			@Override
			public void remove() {
				BitmapContainer.this.remove((short) i);
			}

		};
	}

	@Override
	public int getCardinality() {
		return cardinality;
	}

	public Container and(BitmapContainer value2) {
		BitmapContainer value1 = this;
		BitmapContainer answer = new BitmapContainer();
		for (int k = 0; k < answer.bitmap.length; ++k) // optimiser � max(last
														// set bit of value1,
														// value2)
		{
			answer.bitmap[k] = value1.bitmap[k] & value2.bitmap[k];
			answer.cardinality += Long.bitCount(answer.bitmap[k]);
		}

		if (answer.cardinality < 1024)
			return new ArrayContainer(answer);
		return answer;
	}

	public ArrayContainer and(ArrayContainer value2) 
	{
		BitmapContainer value1 = this;
		ArrayContainer answer = new ArrayContainer();
		for (int k = 0; k < value2.getCardinality(); ++k)
			if (value1.contains(value2.content[k]))
				answer.content[answer.cardinality++] = value2.content[k];
		return answer;
	}

	public BitmapContainer or(ArrayContainer value2) 
	{
		BitmapContainer value1 = this;
		BitmapContainer answer = new BitmapContainer();
		for (int k = 0; k < value2.getCardinality(); ++k)
			if (!value1.contains(value2.content[k])) // si la val de la seq
														// !exist on l'ajoute ds
														// le bitmap
				answer.bitmap[toIntUnsigned(value2.content[k])/64 ] |= (1l << (value2.content[k] % 64));								
		answer.cardinality = answer.expensiveComputeCardinality();
		return answer;
	}

	public Container or(BitmapContainer value2) {
		BitmapContainer value1 = this;
		BitmapContainer answer = new BitmapContainer();
		for (int k = 0; k < answer.bitmap.length; ++k) // optimise to min(last
														// set bit of value1 and
														// value2)
		{
			answer.bitmap[k] = value1.bitmap[k] | value2.bitmap[k];
			answer.cardinality += Long.bitCount(answer.bitmap[k]);
		}
		if (answer.cardinality < 1024)
			return new ArrayContainer(answer);
		return answer;
	}

	@Override
	public int getSizeInBits() {
		//the standard size is 1024 chunks*64bits each=65536 bits, 
		//each 1 bit represents an integer between 0 and 65535
		return 65536; 
	}

	public Container xor(ArrayContainer value2) // intersect de 2 seq d'entiers
	{
		BitmapContainer value1 = this;
		BitmapContainer answer = new BitmapContainer();
		for (int k = 0; k < value2.getCardinality(); ++k)
			if (!value1.contains(value2.content[k])) // si la val de la seq
														// !exist on l'ajoute ds
														// le bitmap
			{
				answer.bitmap[toIntUnsigned(value2.content[k])/64 ] |= (1l << (value2.content[k] % 64));
				answer.cardinality++;
			} else
				answer.bitmap[toIntUnsigned(value2.content[k])/64] &= ~(1l << (value2.content[k] % 64));

		if (answer.cardinality < 1024)
			return new ArrayContainer(answer);
		return answer;
	}

	public Container xor(BitmapContainer value2) {
		BitmapContainer value1 = this;
		BitmapContainer answer = new BitmapContainer();
		for (int k = 0; k < answer.bitmap.length; ++k) {
			answer.bitmap[k] = value1.bitmap[k] ^ value2.bitmap[k];
			answer.cardinality += Long.bitCount(answer.bitmap[k]);
		}

		if (answer.cardinality < 1024)
			return new ArrayContainer(answer);
		return answer;
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		int counter = 0;
		sb.append("{");
		int i = this.nextSetBit(0);
		while (i >= 0) {
			sb.append(i);
			++counter;
			i = this.nextSetBit(i + 1);
			if (i >= 0)
				sb.append(",");
		} 
		sb.append("}");
		System.out.println("cardinality = "+cardinality+" "+counter);
		return sb.toString();
	}

	private int expensiveComputeCardinality() {
		int counter = 0;
		for(long x : this.bitmap)
			counter += Long.bitCount(x);
		return counter;
	}
	
	
	@Override
	public void validate() {
		int counter = 0;
		{
			int i = this.nextSetBit(0);
			while (i >= 0) {
				++counter;
				i = this.nextSetBit(i + 1);
			}
		}
		if(expensiveComputeCardinality() != counter) throw
		new RuntimeException("problem");
		if(expensiveComputeCardinality()!= cardinality) throw new RuntimeException(expensiveComputeCardinality()+" "+cardinality);
	}
	 
	@Override
	public BitmapContainer clone() {
		try {
			BitmapContainer x = (BitmapContainer) super.clone();
			x.cardinality = this.cardinality;
			x.bitmap = Arrays.copyOf(bitmap,bitmap.length);
			return x;
		} catch (CloneNotSupportedException e) {
			throw new java.lang.RuntimeException();
		}
	}

}
