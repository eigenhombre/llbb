README.md: preamble.md *.bb ex*/*.md ex*/*.cmd
	cp preamble.md README.md
	./build.bb >> README.md
