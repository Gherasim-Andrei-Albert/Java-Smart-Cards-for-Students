package terminal;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.javacard.apduio.Apdu;
import com.sun.javacard.apduio.CadClientInterface;
//import com.sun.javacard.apduio;
import com.sun.javacard.apduio.CadDevice;
import com.sun.javacard.apduio.CadTransportException;

public class Main {
	
	static Apdu apdu;
	static CadClientInterface cad;
	
	static void stringLineToApdu(String line) {
	
		String[] bytesStrings = line.split(";")[0].split(" ");
		byte[] lineBytes = new byte[bytesStrings.length];
		short byteStringIndex = 0;
		for(String byteString : bytesStrings) {
			lineBytes[byteStringIndex ++] = Integer.decode(byteString).byteValue();
		}

		byte cla = lineBytes[0],
			ins = lineBytes[1],
			p1 = lineBytes[2],
			p2 = lineBytes[3],
			lc = 0, le = 0;

		short apduCase = 0;
		
		if(lineBytes.length == 4) {
			apduCase = 1;
		}
		if(lineBytes.length == 5) {
			apduCase = 2;
			le = lineBytes[4];
		}
		if(lineBytes.length > 5) {
			lc = lineBytes[4];
			if(lineBytes.length == 5 + lc) {
				apduCase = 3;
			}
			else {
				apduCase = 4;
				le = lineBytes[lineBytes.length - 1];
			}
		}
		
		apdu = new Apdu();

		apdu.command = Arrays.copyOfRange(lineBytes, 0, 4);
		
		if(lc == 0) {
			apdu.setDataIn(new byte[] {}, 0);
		}
		else {
			apdu.setDataIn(Arrays.copyOfRange(lineBytes, 5, 5 + lc));
		}
		
		if(apduCase == 2 || apduCase == 4) {
			apdu.Le = le;
		}
	}

	static Apdu executeStringApdu(String line,  CadClientInterface cad) throws IOException, CadTransportException {
		stringLineToApdu(line);
		
		cad.exchangeApdu(apdu);
		
		return apdu;
	}
	
	static void createCard(short studentID, short[] pin) throws IOException, CadTransportException {
		
		if(cad != null) {
			cad.powerDown();
		}
		
		// create simulator
		String crefFilePath = "c:\\Program Files (x86)\\Oracle\\Java Card Development Kit Simulator 3.1.0\\bin\\cref.bat";
		Process process = Runtime.getRuntime().exec(crefFilePath);
		
		// connect to simulator
		Socket sock = new Socket("localhost", 9025);
		InputStream is = sock.getInputStream();
		OutputStream os = sock.getOutputStream();
		cad = CadDevice.getCadClientInstance(CadDevice.PROTOCOL_T1, is, os);
		
		// send power up
		cad.powerUp();
		
		
		// execute cap-Wallet
		
		FileReader fileReader = new FileReader("apdu_scripts/cap-Wallet.script");
		BufferedReader reader = new BufferedReader(fileReader);
		
		String line;
		while((line = reader.readLine()) != null) {
			if(line.startsWith("0x")) {
//				System.out.println(line);
				
				executeStringApdu(line, cad);
				
//				System.out.println(String.format("%02X", apdu.sw1sw2[0])+" "+String.format("%02X", apdu.sw1sw2[1]));
			}
		}
		

		String pinLengthByteString = String.format("0x%02X", pin.length);
		String pinBytesString = "";
		short pinDigitsIndex = 0;
		for(short pinDigit: pin) {
			pinBytesString += String.format("0x%02X", pinDigit);
			if(pinDigitsIndex != pin.length - 1)
				pinBytesString += " ";
			++ pinDigitsIndex;
		}
		
		// create an applet with preset pin and student ID
		executeStringApdu(
			String.format(
				"0x80 0xB8 0x00 0x00 0x1a 0x0a 0xa0 0x0 0x0 0x0 0x62 0x3 0x1 0xc 0x6 0x1 0x0e 0x0 0x0 %s %s %s 0x7F;",
				pinLengthByteString, pinBytesString, shortToBytesString(studentID)
			), 
			cad
		);
		
		// select the applet
		executeStringApdu("0x00 0xA4 0x04 0x00 0x0a 0xa0 0x0 0x0 0x0 0x62 0x3 0x1 0xc 0x6 0x1 0x7F;", cad);
		
	}

	
//	class Simulation {
//		ArrayList<Card> generatedCards = {};
//		Card generateCard(short studentID, short[] pin) {
//			
//		}
//	}
//	
//	class Card {
//		connect(){};
//		verify(){};
//		addGradeRecord();
//	}
	
	
	private static byte[] shortToBytes(final short data) {
	    return new byte[] {
	        (byte)((data >> 8) & 0xff),
	        (byte)((data >> 0) & 0xff)
	    };
	}
	

	private static String shortToBytesString(final short data) {
		byte[] bytes = shortToBytes(data);
	    return String.format("0x%02x 0x%02x", bytes[0], bytes[1]);
	}	

	private static String shortToByteString(final short data) {
	    return String.format("0x%02x", data);
	}
	
	private static String integersStringToBytesString(final String data) {
		return data.chars()
			.map(digitCharCode -> Integer.parseInt(String.valueOf((char) digitCharCode)))
			.mapToObj(digit -> shortToByteString((short) digit))
			.collect(Collectors.joining(" "));
	}
	
	
	static class Subject {
		short ID;
		String name;
		
		public Subject(short ID, String name) {
			this.ID = ID;
			this.name = name;
		}
	}
	
	
	static class Student {
		short ID;
		String name;
		
		public Student(short ID, String name) {
			this.ID = ID;
			this.name = name;
		}
	}
	
	
	public static void main(String[] args) {
		
		try {
//			createCard();
//			
//			// autheticate
//			executeStringApdu("0x80 0x20 0x00 0x00 0x05 0x01 0x02 0x03 0x04 0x05 0x7F;", cad);
//			
//			// display student id
//			executeStringApdu("0x80 0x70 0x00 0x00 0x00 0x7F;", cad);
//
//			// add a grade record
//			executeStringApdu("0x80 0x80 0x00 0x00 0x0d 0x02 0x00 0x01 0x08 0x04 0x04 0x04 0x05 0x02 0x02 0x02 0x02 0x02 0x7F;", cad);
//			
//			// display grade records
//			executeStringApdu("0x80 0x90 0x00 0x00 0x00 0x02;", cad);
//			
//			System.out.println(String.format("%02X", apdu.sw1sw2[0])+" "+String.format("%02X", apdu.sw1sw2[1]));
//			
//			for(short outputByte: apdu.dataOut) {
//				System.out.print(outputByte + " ");
//			} System.out.println();
//			
//			cad.powerDown();
			
			
			
			Class.forName("com.mysql.cj.jdbc.Driver");
			Connection connection = DriverManager.getConnection("jdbc:mysql://localhost/student_sc?"
                + "user=root&password=");
			
			
//			ArrayList<Short> studentsIDs = new ArrayList<Short>();
			
//			Scanner studentsScanner = new Scanner(new File("database/students.csv"));
//			studentsScanner.nextLine();
//			while (studentsScanner.hasNextLine()) {
//				studentsIDs.add(studentsScanner.nextShort());
//		    }
			
			// Statements allow to issue SQL queries to the database
			PreparedStatement preparedStatement;
			Statement statement = connection.createStatement();
			
            // Result set get the result of the SQL query
			ResultSet resultSet = statement
                    .executeQuery("SELECT * FROM student_sc.students");
			
			ArrayList<Student> students = new ArrayList<Student>();
			while (resultSet.next()) {
				students.add(new Student(resultSet.getShort(1), resultSet.getString(2)));
			}
			
			// Statements allow to issue SQL queries to the database
			statement = connection.createStatement();
			
            // Result set get the result of the SQL query
			resultSet = statement
                .executeQuery("SELECT * FROM student_sc.subjects");
			
			ArrayList<Subject> subjects = new ArrayList<Subject>();
			while (resultSet.next()) {
				subjects.add(new Subject(resultSet.getShort(1), resultSet.getString(2)));
			}
			
			boolean exit = false;
			while(!exit) {
				
				String[] menuItems = {
					"Profesori",
					"Secretariat",
					"Iesire"
				};
				
				short menuItemIndex = 1;
				for(String menuItem: menuItems) {
					System.out.println(menuItemIndex + ". " + menuItem);
					++ menuItemIndex;
				}
				System.out.println();
				
				Scanner scanner = new Scanner(System.in);
				short selectedItemIndex = (short) (scanner.nextInt());
				if(selectedItemIndex == 3) {
					exit = true;
					continue;
				}
				
				boolean isTeacher = selectedItemIndex == 1;
				
				boolean back = false;
				while(!back) {
				
					System.out.println("\nApasati tasta pentru una din optiunile de mai jos:\n");
					
					menuItems = new String[]{
						"Simuleaza conectarea unui card",
						"Inapoi"
					};
					
					menuItemIndex = 1;
					for(String menuItem: menuItems) {
						System.out.println(menuItemIndex + ". " + menuItem);
						++ menuItemIndex;
					}
					System.out.println();
					
					selectedItemIndex = (short) (scanner.nextInt());
					
					switch(selectedItemIndex) {
						case 1:
							System.out.println("Apasati tasta pentru studentul caruia doriti sa i se simuleze cardul:\n");
							
							short studentIndex = 1;
							for(Student student: students) {
								System.out.println(studentIndex + ". " + student.name);
								++ studentIndex;
							}
							System.out.println();
							short selectedStudentIndex = (short) (scanner.nextInt() - 1);
							Student selectedStudent = students.get(selectedStudentIndex);
							
							// generate card credentials
							short[] generatedPin;
							if(selectedStudent.ID < 4) {
								short[][] pinSamples = {
									{1, 2, 3, 4, 5},
									{1, 1, 1, 1, 1},
									{5, 4, 3, 2, 1},
									{5, 5, 5, 5, 5}
								};
								generatedPin = pinSamples[selectedStudent.ID];
							}
							else {
								generatedPin = new short[] {0, 0, 0, 0, 0};
							}
							createCard(selectedStudent.ID, generatedPin);
							
							System.out.println("Tastati pin-ul:");
							String pin = scanner.next();
							
							String pinLenghtByteString = shortToByteString((short) pin.length());
							String pinBytesString = integersStringToBytesString(pin);
							
							// verify pin
							executeStringApdu(
								String.format("0x80 0x20 0x00 0x00 %s %s 0x7F;", pinLenghtByteString, pinBytesString), 
								cad
							);
							
							System.out.println(String.format("%02X", apdu.sw1sw2[0])+" "+String.format("%02X", apdu.sw1sw2[1]));
							
							if(apdu.sw1sw2[0] == (byte) 0x90 && apdu.sw1sw2[1] == (byte) 0x00) {
								System.out.println("Pin acceptat.\n");

								
								executeStringApdu("0x80 0x70 0x00 0x00 0x00 0x7F;", cad);
								
								byte[] studentIDBytes = new byte[2];
								studentIDBytes[0] = apdu.dataOut[0];
								studentIDBytes[1] = apdu.dataOut[1];
								
								short studentID = (short) ((0xff & studentIDBytes[0]) << 8 | (0xff & studentIDBytes[1]) << 0);
								
								
								if(isTeacher) {
									System.out.println("Intoduceti tasta pentru una dintre materiile de mai jos:\n");
									
									subjects.forEach((subject) -> {
										System.out.println(String.format("%d. %s", subject.ID + 1, subject.name));
									});
									
									short selectedSubjectIndex = (short) (scanner.nextShort() - 1);
									Subject selectedSubject = subjects.get(selectedSubjectIndex);
									
									System.out.println("Intoduceti nota:\n");
									short grade = scanner.nextShort();
									
									System.out.println("Intoduceti numarul examenului, 1 pentru primul, 2 pentru al doilea, 3 pentru al treilea:\n");
									short examNumber = scanner.nextShort();
									
									byte day, month, year;
									
									LocalDateTime now = LocalDateTime.now();
									day = (byte) (now.getDayOfMonth() + 1);
									month = (byte) now.getMonthValue();
									year = (byte) (now.getYear() - 2022);
									
									// add grade
									executeStringApdu(
										String.format(
											"0x80 0x80 0x00 0x00 0x06 0x%02x 0x%02x 0x%02x 0x%02x 0x%02x 0x%02x 0x7F;",
											selectedSubject.ID, grade, examNumber, day, month, year
										), 
										cad
									);
									
									System.out.println(String.format("%02X", apdu.sw1sw2[0])+" "+String.format("%02X", apdu.sw1sw2[1]));
									
									preparedStatement = connection
						                    .prepareStatement("SELECT count(*) FROM student_sc.students_subjects_grades WHERE studentID=? AND subjectID=?");
						            // "myuser, webpage, datum, summary, COMMENTS from feedback.comments");
						            // Parameters start with 1
						            preparedStatement.setShort(1, studentID);
						            preparedStatement.setShort(2, selectedSubject.ID);
						            resultSet = preparedStatement.executeQuery();
						            
						            resultSet.next();
						            boolean studentSubjectGradesRowExists = resultSet.getInt(1) != 0;
						            
						            if(!studentSubjectGradesRowExists) {
						            	preparedStatement = connection
							                    .prepareStatement("insert into student_sc.students_subjects_grades (studentID, subjectID) values (?, ?)");
							            // "myuser, webpage, datum, summary, COMMENTS from feedback.comments");
							            // Parameters start with 1
							            preparedStatement.setShort(1, studentID);
							            preparedStatement.setShort(2, selectedSubject.ID);
							            preparedStatement.executeUpdate();
						            }

						            String examGradeFieldName = "grade" + examNumber;
						            String dateGradeFieldName = "date" + examNumber;
						            
						            preparedStatement = connection
						                    .prepareStatement("update student_sc.students_subjects_grades set studentID=?, subjectID=?, " + examGradeFieldName + "=?, " + dateGradeFieldName + "=CURDATE()");
						            // "myuser, webpage, datum, summary, COMMENTS from feedback.comments");
						            // Parameters start with 1
						            preparedStatement.setShort(1, studentID);
						            preparedStatement.setShort(2, selectedSubject.ID);
						            preparedStatement.setShort(3, grade);
						            preparedStatement.executeUpdate();
								}
								else {
									
						            executeStringApdu("0x80 0x90 0x00 0x00 0x00 0x02;", cad);
									byte[] smartCardGrades = apdu.dataOut;
									
									
									preparedStatement = connection
						                    .prepareStatement("SELECT * FROM student_sc.students_subjects_grades WHERE studentID=?");
						            // "myuser, webpage, datum, summary, COMMENTS from feedback.comments");
						            // Parameters start with 1
						            preparedStatement.setShort(1, studentID);
						            resultSet = preparedStatement.executeQuery();
						            
						            while(resultSet.next()) {
						            	short subjectID = resultSet.getShort("subjectID");
						            	short[] DBGrades = new short[] { resultSet.getShort("grade1"), resultSet.getShort("grade2"), resultSet.getShort("grade3") };
						            	LocalDate[] DBDates = new LocalDate[3];
						            	for(short DBDatesIndex = 0; DBDatesIndex < 3; ++ DBDatesIndex) {
						            		java.sql.Date date = resultSet.getDate("date" + (DBDatesIndex + 1));
						            		DBDates[DBDatesIndex] = date == null ? null : date.toLocalDate();
						            	}
						            	
						            	for (short DBGradesIndex = 0; DBGradesIndex < 3; ++ DBGradesIndex) {
						            		if(DBGrades[DBGradesIndex] != smartCardGrades[subjectID * 3 + DBGradesIndex]) {
						            			System.out.println("syncing...");
						            			
						            			byte day, month, year;
												
												day = (byte) (DBDates[DBGradesIndex].getDayOfMonth() + 1);
												month = (byte) DBDates[DBGradesIndex].getMonthValue();
												year = (byte) (DBDates[DBGradesIndex].getYear() - 2022);
												
//												System.out.println(
//													String.format(
//														"0x80 0x80 0x00 0x00 0x06 0x%02x 0x%02x 0x%02x 0x%02x 0x%02x 0x%02x 0x7F;",
//														subjectID, DBGrades[DBGradesIndex], DBGradesIndex + 1, day, month, year
//													)
//												);
												
												// add grade
												executeStringApdu(
													String.format(
														"0x80 0x80 0x00 0x00 0x06 0x%02x 0x%02x 0x%02x 0x%02x 0x%02x 0x%02x 0x7F;",
														subjectID, DBGrades[DBGradesIndex], DBGradesIndex + 1, day, month, year
													), 
													cad
												);
												
												System.out.println(String.format("%02X", apdu.sw1sw2[0])+" "+String.format("%02X", apdu.sw1sw2[1]));
						            		}
						            	}
						            	
						            	System.out.println("done");
						            }

									
								}
							}
							else {
								System.out.println("Pin incorect.");
							}

							break;
						case 2:
							back = true;
							break;
					}
				}
			}
		} 
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		catch (CadTransportException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
	}
}
