; Test case: 
; - lines 4-6 should be optimized

	pop bc
	ld d,b
	ld e,c
	ld (var1),de

var1:
	dw 0