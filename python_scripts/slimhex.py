import socket
import struct
import codecs

#plugs
p  = "acdc::50:c4ff:fe04:8431" 
pp = "acdc::50:c4ff:fe04:8310"

#source
source = "acdc::50:c4ff:fe04:2a3"
source_port = 49999

#socket placeholder
inc = socket.socket(socket.AF_INET6, socket.SOCK_DGRAM, 17)

def getSocket():
    return (socket.socket(socket.AF_INET6, socket.SOCK_DGRAM, 17))

def chooseTarget(): 
	while (True):
		x = input("Set Target please (plug or plug+): ")
		if (x == "plug"):
			return p
		elif (x == "plug+"):
			return pp

def main():
	while (True):	
		x = input("Input please: ")
		if (x == "change"):
			chooseTarget()

		elif (x == "on"):
			out = getSocket()
			out.bind((source, source_port))
			byte = "48583043040000000001010157b6"
			msg = codecs.decode(byte, "hex_codec")
			print(msg)
			print ("getsockname: " + str(out.getsockname()))
			out.sendto(msg, (target, target_port))
			out.close()
        
		elif (x == "off"):
			out = getSocket()
			out.bind((source, source_port))
			byte = "485830430400000000010100463fff"
			msg = codecs.decode(byte, "hex_codec")
			print(msg)
			print("getsockname: " + str(out.getsockname()))
			out.sendto(msg, (target, target_port))
			out.close()

		elif (x == "get"):
			if (target == pp):
				out = getSocket()
				out.bind((source, source_port))
				byte = "48583043020000000002f7cb"   
				msg = codecs.decode(byte, "hex_codec")
				print(msg)
				out.sendto(msg, (target, target_port))
				data, addr = out.recvfrom(256)
				i = 0
				for d in data:
					print(i, d)
					i = i + 1
				val =  data[11] + data[12] + data[13] + data[14]
				print("Momentaner Verbrauch: ", val)
				out.close()				
			else:
				print("Please check if the chosen target supports 'get'")
		elif (x == "quit"):
			print("Exitting ...")
			exit()

if __name__ == "__main__":
	target = chooseTarget()
	target_port = 61616
	main()
