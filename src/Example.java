
public class Example {

	public Example() {
		System.out.println("constructed example");
	}

	public static void main(String[] args) {
		System.out.println(tableSwitch());
	}

	public static int test(int a, double d, int b, float f, long l, String s2) {
		if (a == 6) {
			return 6;
		}
		short s;
		try {
			if (a == 7) {
				return a + 1;
			}
		} finally {
			s = 2;
		}
		return s;
	}

	public static int loop() {
		int n = 5;
		for (int i = 0; i < 5; i++) {
			n = n + i;
		}
		return n;
	}

	public static void arrays() {
		int[] a = new int[2];
		long[] b = new long[45];
		float[] c = new float[300];
		double[] d = new double[99999];
		byte[] e = new byte[2];
		boolean[] f = new boolean[2];
		char[] g = new char[2];
		String[] h = new String[2];

		int v = d.length;
		int v2 = c.length;
		int v3 = h.length;

		String s = h[0];

		String[][][] s3d = new String[1][2][3];
		int[][][] i3d = new int[3][2][1];

		int[][] i2d = i3d[0];

		int len = i2d.length;
		System.out.println(len);
	}

	public static void math() {
		int nine = 9;
		int imul = 2 * nine;
		int iadd = 2 + nine;
		int isub = 2 - nine;
		int irem = nine % 2;
		int idiv = nine / 2;
		int one = 1;
		int zero = 0;
		int isl = one << one;
		int isr = -1 >> one;
		int iusr = -1 >>> one;
		int ior = one | 0;
		int iand = one & 0;
		int ixor = one ^ one;
		double two = 2.0;
		double dmul = 4.5 * two;
	}

	public static void casts() {
		int i = 34;
		long l = 34;
		float f = 34;
		double d = 34;

		int a = (int) d;
		long b = (long) f;
		char c = (char) f;
		byte d_ = (byte) f;
		short e = (short) f;
		float f_ = (float) l;
		double g_ = (double) i;
	}

	public static int longLoop() {
		int total = 0;
		for (int i = 0; i < 1000; i++) {
			total += i;
		}

		return total;
	}

	public static int tableSwitch() {
		int c = 0;
		for (int i = 0; i < 4; i++) {
			switch (i) {
				case 1:
					c += 2;
					break;
				case 2:
					c += 1;
					break;
				case 3:
					c += 2;
					break;
				default:
					c++;

			}
		}

		return c;

	}

}
