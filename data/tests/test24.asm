; Test case: check code annotations work


IFDEF symbol
	ld b,1	; mdl:no-opt
	ld c,2
ELSE
	ld b,3
	ld c,4
ENDIF
	ld (symbol),bc

end:
	jp end

symbol:
	dw 0
