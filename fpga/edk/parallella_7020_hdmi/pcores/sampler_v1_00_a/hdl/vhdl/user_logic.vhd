------------------------------------------------------------------------------
-- Sampler, a logic analyzer entity
--
-- samples 64 digital input signals to an internal 256 kb block RAM (= 32 k samples).
--
-- Typical Usage: set RISING_TRIGGER, FALLING_TRIGGER, TRIGGER_MASK, WRITE_INDEX
-- and SAMPLE_COUNTER to 0, then set CONTROL to 1 to start sampling.
-- When the sampling is running, WRITE_INDEX is incremented each cycle. The
-- block RAM is a ringbuffer and it wraps at the end to the start, filling the
-- RAM continously when sampling is enabled.
--
-- To read the samples, first stop the sampling (write 0 to CONTROL). Then read the
-- number of samples from SAMPLE_COUNTER. Use WRITE_INDEX to calculate the position
-- in the ringbuffer of the last valid sample. Then read the samples from the ringbuffer.
--
-- The 100 MHz samplerate can be reduced by the DIVIDER register.
--
-- The block RAM is a dual port block RAM, so you can read the memory while sampling
-- as well.
--
-- Trigger usage: before start sampling, set the trigger condition. When the trigger
-- condition is detected (all trigger conditions are logically OR'ed), the trigger
-- bit is set in the control register and for each new sample, TRIGGER_DELAY is
-- decremented. When TRIGGER_DELAY is 0, the sampling is stopped. This can be used
-- to set the sample position, to determine how many samples after and before a trigger
-- should be available.
--
-- The trigger bit in the control register has to be reset by software. You can set
-- the trigger bit as well, e.g. to sample a fixed number of samples after starting
-- the sampling.
--
-- For testing you can enable a test counter and route it to the input signals, instead
-- of using the real input signals. The test counter is always incremented with 100 MHz.
--
-- The Bus2IP_Addr is local in this entity (mapped at physical location 0x40000000).
-- a 32 MB memory map is reserved:
--
-- 0x00000000-0x00ffffff sample memory (only first 0x0004_0000 bytes are valid)
-- 0x01000000-0x01ffffff registers (32 registers with 32 bits each, mirrored in the memory)
--
-- see "constant" definitions below for a detailed register description
--

-- XPS generated
-- DO NOT EDIT BELOW THIS LINE --------------------
library ieee;
use ieee.std_logic_1164.all;
use ieee.std_logic_arith.all;
use ieee.std_logic_unsigned.all;

library proc_common_v3_00_a;
use proc_common_v3_00_a.proc_common_pkg.all;

-- DO NOT EDIT ABOVE THIS LINE --------------------

--USER libraries added here
use ieee.math_real.all;

------------------------------------------------------------------------------
-- Entity section
------------------------------------------------------------------------------
-- Definition of Generics:
--       C_SLV_AWIDTH                   -- Slave interface address bus width
--       C_SLV_DWIDTH                   -- Slave interface data bus width
--       C_NUM_MEM                      -- Number of memory spaces
--
-- Definition of Ports:
--       INPUT                          -- 64 bit digital input signals for sampling
--       DBG                            -- debug output for testing
--       Bus2IP_Clk                     -- Bus to IP clock (100 MHz)
--       Bus2IP_Resetn                  -- Bus to IP reset
--       Bus2IP_Addr                    -- Bus to IP address bus
--       Bus2IP_CS                      -- Bus to IP chip select for user logic memory selection
--       Bus2IP_RNW                     -- Bus to IP read/not write
--       Bus2IP_Data                    -- Bus to IP data bus
--       Bus2IP_BE                      -- Bus to IP byte enables
--       Bus2IP_RdCE                    -- Bus to IP read chip enable
--       Bus2IP_WrCE                    -- Bus to IP write chip enable
--       Bus2IP_Burst                   -- Bus to IP burst-mode qualifier
--       Bus2IP_BurstLength             -- Bus to IP burst length
--       Bus2IP_RdReq                   -- Bus to IP read request
--       Bus2IP_WrReq                   -- Bus to IP write request
--       Type_of_xfer                   -- Transfer Type
--       IP2Bus_AddrAck                 -- IP to Bus address acknowledgement
--       IP2Bus_Data                    -- IP to Bus data bus
--       IP2Bus_RdAck                   -- IP to Bus read transfer acknowledgement
--       IP2Bus_WrAck                   -- IP to Bus write transfer acknowledgement
--       IP2Bus_Error                   -- IP to Bus error response
------------------------------------------------------------------------------

entity user_logic is
	generic
		(
			-- ADD USER GENERICS BELOW THIS LINE ---------------
			--USER generics added here
			-- ADD USER GENERICS ABOVE THIS LINE ---------------

			-- DO NOT EDIT BELOW THIS LINE ---------------------
			-- Bus protocol parameters, do not add to or delete
			C_SLV_AWIDTH : integer := 32;
			C_SLV_DWIDTH : integer := 32;
			C_NUM_MEM    : integer := 1
			-- DO NOT EDIT ABOVE THIS LINE ---------------------
			);
	port
		(
			-- ADD USER PORTS BELOW THIS LINE ------------------
			--USER ports added here
			-- ADD USER PORTS ABOVE THIS LINE ------------------
			INPUT : in  std_logic_vector(63 downto 0);
			DBG   : out std_logic_vector(7 downto 0);

			-- DO NOT EDIT BELOW THIS LINE ---------------------
			-- Bus protocol ports, do not add to or delete
			Bus2IP_Clk         : in  std_logic;
			Bus2IP_Resetn      : in  std_logic;
			Bus2IP_Addr        : in  std_logic_vector(C_SLV_AWIDTH-1 downto 0);
			Bus2IP_CS          : in  std_logic_vector(C_NUM_MEM-1 downto 0);
			Bus2IP_RNW         : in  std_logic;
			Bus2IP_Data        : in  std_logic_vector(C_SLV_DWIDTH-1 downto 0);
			Bus2IP_BE          : in  std_logic_vector(C_SLV_DWIDTH/8-1 downto 0);
			Bus2IP_RdCE        : in  std_logic_vector(C_NUM_MEM-1 downto 0);
			Bus2IP_WrCE        : in  std_logic_vector(C_NUM_MEM-1 downto 0);
			Bus2IP_Burst       : in  std_logic;
			Bus2IP_BurstLength : in  std_logic_vector(7 downto 0);
			Bus2IP_RdReq       : in  std_logic;
			Bus2IP_WrReq       : in  std_logic;
			Type_of_xfer       : in  std_logic;
			IP2Bus_AddrAck     : out std_logic;
			IP2Bus_Data        : out std_logic_vector(C_SLV_DWIDTH-1 downto 0);
			IP2Bus_RdAck       : out std_logic;
			IP2Bus_WrAck       : out std_logic;
			IP2Bus_Error       : out std_logic
			-- DO NOT EDIT ABOVE THIS LINE ---------------------
			);

	attribute MAX_FANOUT : string;
	attribute SIGIS      : string;

	attribute SIGIS of Bus2IP_Clk    : signal is "CLK";
	attribute SIGIS of Bus2IP_Resetn : signal is "RST";

end entity user_logic;

------------------------------------------------------------------------------
-- Architecture section
------------------------------------------------------------------------------

architecture IMP of user_logic is

	--USER signal declarations added here, as needed for user logic

	-- 32 ksamples memory, 64 bits per sample
	constant MEM_SIZE : integer := 16#8000#;
	constant MEM_BITS : integer := integer(ceil(log2(real(MEM_SIZE))+0.1));

	-- bit 0: run sampling: set to 1 to run sampling, set to 0 to stop sampling, trigger sets it to 0
	-- bit 1: trigger bit: 1, if triggered, counts down TRIGGER_DELAY until stop. 0 if not triggered
	-- bit 2: run test counter
	-- bit 3: route test counter to low 32 bit of INPUT and inverted test counter to high 32 bit of INPUT
	constant CONTROL : integer := 0;

	-- current or last write index
	constant WRITE_INDEX : integer := 1;

	-- samplerate is 100 MHz / samplerate_divider
	constant SAMPLERATE_DIVIDER : integer := 2;

	-- 1 for each bit on which it should trigger on rising edge (OR'ed together)
	constant RISING_TRIGGER_LOW  : integer := 3;
	constant RISING_TRIGGER_HIGH : integer := 4;

	-- 1 for each bit on which it should trigger on falling edge (OR'ed together)
	constant FALLING_TRIGGER_LOW  : integer := 5;
	constant FALLING_TRIGGER_HIGH : integer := 6;

	-- 1 for each bit, which should be compared with trigger_pattern for a trigger
	constant TRIGGER_MASK_LOW  : integer := 7;
	constant TRIGGER_MASK_HIGH : integer := 8;

	-- trigger, if this trigger pattern for the masked bits is detected
	constant TRIGGER_PATTERN_LOW  : integer := 9;
	constant TRIGGER_PATTERN_HIGH : integer := 10;

	-- number of words to sample after trigger detection and until stop
	constant TRIGGER_DELAY : integer := 11;

	-- number of sampled words since last start (this counter stops at 0x4000)
	constant SAMPLE_COUNTER : integer := 12;

	-- current input levels
	constant INPUT_STATE_LOW  : integer := 13;
	constant INPUT_STATE_HIGH : integer := 14;

	-- 32 bit test counter
	constant TEST_COUNTER : integer := 15;

	-- ID/version of this module (high word: always 1 / low word: version)
	constant VERSION       : integer                       := 31;
	constant VERSION_VALUE : std_logic_vector(31 downto 0) := X"00010002";

	signal control_reg            : std_logic_vector(31 downto 0);
	signal write_index_reg        : std_logic_vector(31 downto 0);
	signal samplerate_divider_reg : std_logic_vector(31 downto 0);
	signal rising_trigger_reg     : std_logic_vector(63 downto 0);
	signal falling_trigger_reg    : std_logic_vector(63 downto 0);
	signal trigger_mask_reg       : std_logic_vector(63 downto 0);
	signal trigger_pattern_reg    : std_logic_vector(63 downto 0);
	signal trigger_delay_reg      : std_logic_vector(31 downto 0);
	signal sample_counter_reg     : std_logic_vector(31 downto 0);
	signal input_state_reg        : std_logic_vector(63 downto 0);
	signal test_counter_reg       : std_logic_vector(31 downto 0);

	signal last_input_state   : std_logic_vector(63 downto 0);
	signal write_enable       : std_logic;
	signal samplerate_counter : std_logic_vector(31 downto 0);

	------------------------------------------
	-- Signals for user logic memory space example
	------------------------------------------
	type BYTE_RAM_TYPE is array (0 to MEM_SIZE - 1) of std_logic_vector(7 downto 0);
	type DO_TYPE is array (0 to C_NUM_MEM-1) of std_logic_vector(63 downto 0);
	signal mem_data_out      : DO_TYPE;
	signal mem_data_in       : DO_TYPE;
	signal mem_write_address : std_logic_vector(MEM_BITS - 1 downto 0);
	signal mem_select        : std_logic_vector(0 to 0);
	signal mem_read_enable   : std_logic;
	signal mem_ip2bus_data   : std_logic_vector(C_SLV_DWIDTH-1 downto 0);
	signal mem_read_ack_dly1 : std_logic;
	signal mem_read_ack_dly2 : std_logic;
	signal mem_read_ack      : std_logic;
	signal mem_write_ack     : std_logic;

begin
	--USER logic implementation added here

	-- block RAM interface
	mem_select        <= Bus2IP_CS;
	mem_read_enable   <= (Bus2IP_RdCE(0));
	mem_read_ack      <= mem_read_ack_dly1 and (not mem_read_ack_dly2);
	mem_write_ack     <= (Bus2IP_WrCE(0));
	mem_write_address <= write_index_reg(MEM_BITS - 1 downto 0);

	-- this process generates the read acknowledge 1 clock after read enable
	-- is presented to the BRAM block. The BRAM block has a 1 clock delay
	-- from read enable to data out.
	BRAM_RD_ACK_PROC : process(Bus2IP_Clk) is
	begin
		if (Bus2IP_Clk'event and Bus2IP_Clk = '1') then
			if (Bus2IP_Resetn = '0') then
				mem_read_ack_dly1 <= '0';
				mem_read_ack_dly2 <= '0';
			else
				mem_read_ack_dly1 <= mem_read_enable;
				mem_read_ack_dly2 <= mem_read_ack_dly1;
			end if;
		end if;
	end process BRAM_RD_ACK_PROC;

	-- this code infers Xilinx block RAM
	BRAM_GEN : for i in 0 to C_NUM_MEM-1 generate
		constant NUM_BYTE_LANES : integer := 8;
	begin
		BYTE_BRAM_GEN : for byte_index in 0 to NUM_BYTE_LANES-1 generate
			signal ram          : BYTE_RAM_TYPE;
			signal data_in      : std_logic_vector(7 downto 0);
			signal data_out     : std_logic_vector(7 downto 0);
			signal read_address : std_logic_vector(MEM_BITS - 1 downto 0);
		begin
			data_in <= mem_data_in(i)(byte_index*8+7 downto byte_index*8);
			BYTE_RAM_PROC : process(Bus2IP_Clk) is
			begin

				if (Bus2IP_Clk'event and Bus2IP_Clk = '1') then
					if (write_enable = '1') then
						ram(CONV_INTEGER(mem_write_address)) <= data_in;
					end if;
					read_address <= Bus2IP_Addr(MEM_BITS + 2 downto 3);
				end if;

			end process BYTE_RAM_PROC;

			data_out <= ram(CONV_INTEGER(read_address));
			mem_data_out(i)(byte_index*8+7 downto byte_index*8) <= data_out;
		end generate BYTE_BRAM_GEN;
	end generate BRAM_GEN;

	-- implement Block RAM and register read mux
	process(mem_data_out, mem_select) is
	begin
		if mem_select = "1" then
			if Bus2IP_Addr(24) = '0' then
				-- memory read
				if Bus2IP_Addr(2) = '0' then
					mem_ip2bus_data <= mem_data_out(0)(31 downto 0);
				else
					mem_ip2bus_data <= mem_data_out(0)(63 downto 32);
				end if;
			else
				-- register read
				case Bus2IP_Addr(6 downto 2) is
					when conv_std_logic_vector(CONTROL, 5) =>
						mem_ip2bus_data <= control_reg;
					when conv_std_logic_vector(WRITE_INDEX, 5) =>
						mem_ip2bus_data <= write_index_reg;
					when conv_std_logic_vector(SAMPLERATE_DIVIDER, 5) =>
						mem_ip2bus_data <= samplerate_divider_reg;
					when conv_std_logic_vector(RISING_TRIGGER_LOW, 5) =>
						mem_ip2bus_data <= rising_trigger_reg(31 downto 0);
					when conv_std_logic_vector(RISING_TRIGGER_HIGH, 5) =>
						mem_ip2bus_data <= rising_trigger_reg(63 downto 32);
					when conv_std_logic_vector(FALLING_TRIGGER_LOW, 5) =>
						mem_ip2bus_data <= falling_trigger_reg(31 downto 0);
					when conv_std_logic_vector(FALLING_TRIGGER_HIGH, 5) =>
						mem_ip2bus_data <= falling_trigger_reg(63 downto 32);
					when conv_std_logic_vector(TRIGGER_MASK_LOW, 5) =>
						mem_ip2bus_data <= trigger_mask_reg(31 downto 0);
					when conv_std_logic_vector(TRIGGER_MASK_HIGH, 5) =>
						mem_ip2bus_data <= trigger_mask_reg(63 downto 32);
					when conv_std_logic_vector(TRIGGER_PATTERN_LOW, 5) =>
						mem_ip2bus_data <= trigger_pattern_reg(31 downto 0);
					when conv_std_logic_vector(TRIGGER_PATTERN_HIGH, 5) =>
						mem_ip2bus_data <= trigger_pattern_reg(63 downto 32);
					when conv_std_logic_vector(TRIGGER_DELAY, 5) =>
						mem_ip2bus_data <= trigger_delay_reg;
					when conv_std_logic_vector(SAMPLE_COUNTER, 5) =>
						mem_ip2bus_data <= sample_counter_reg;
					when conv_std_logic_vector(TEST_COUNTER, 5) =>
						mem_ip2bus_data <= test_counter_reg;
					when conv_std_logic_vector(INPUT_STATE_LOW, 5) =>
						mem_ip2bus_data <= input_state_reg(31 downto 0);
					when conv_std_logic_vector(INPUT_STATE_HIGH, 5) =>
						mem_ip2bus_data <= input_state_reg(63 downto 32);
					when conv_std_logic_vector(VERSION, 5) =>
						mem_ip2bus_data <= VERSION_VALUE;
					when others =>
						mem_ip2bus_data <= (others => '0');
				end case;
			end if;
		else
			mem_ip2bus_data <= (others => '0');
		end if;
	end process;

	-- implement register write and trigger logic
	process(mem_data_out, mem_select) is
	begin
		if rising_edge(Bus2IP_Clk) then
			if Bus2IP_Resetn = '0' then
				control_reg <= (others => '0');
				write_index_reg <= (others => '0');
				samplerate_divider_reg <= (others => '0');
				rising_trigger_reg <= (others => '0');
				falling_trigger_reg <= (others => '0');
				trigger_mask_reg <= (others => '0');
				trigger_pattern_reg <= (others => '0');
				trigger_delay_reg <= (others => '0');
				sample_counter_reg <= (others => '0');
				test_counter_reg <= (others => '0');
				samplerate_counter <= (others => '0');
			else
				-- latch input with the configured samplerate
				if samplerate_counter = 0 then
					if control_reg(3) = '0' then
						input_state_reg <= INPUT;
					else
						input_state_reg <= (not test_counter_reg) & test_counter_reg;
					end if;
					last_input_state <= input_state_reg;
				end if;

				-- write, only full 32 bit access
				if Bus2IP_WrCE(0) = '1' and Bus2IP_BE = "1111" then
					if Bus2IP_Addr(24) = '1' then
						-- write register
						case Bus2IP_Addr(6 downto 2) is
							when conv_std_logic_vector(CONTROL, 5) =>
								control_reg <= Bus2IP_Data;
							when conv_std_logic_vector(WRITE_INDEX, 5) =>
								write_index_reg <= Bus2IP_Data;
							when conv_std_logic_vector(SAMPLERATE_DIVIDER, 5) =>
								samplerate_divider_reg <= Bus2IP_Data;
							when conv_std_logic_vector(RISING_TRIGGER_LOW, 5) =>
								rising_trigger_reg(31 downto 0) <= Bus2IP_Data;
							when conv_std_logic_vector(RISING_TRIGGER_HIGH, 5) =>
								rising_trigger_reg(63 downto 32) <= Bus2IP_Data;
							when conv_std_logic_vector(FALLING_TRIGGER_LOW, 5) =>
								falling_trigger_reg(31 downto 0) <= Bus2IP_Data;
							when conv_std_logic_vector(FALLING_TRIGGER_HIGH, 5) =>
								falling_trigger_reg(63 downto 32) <= Bus2IP_Data;
							when conv_std_logic_vector(TRIGGER_MASK_LOW, 5) =>
								trigger_mask_reg(31 downto 0) <= Bus2IP_Data;
							when conv_std_logic_vector(TRIGGER_MASK_HIGH, 5) =>
								trigger_mask_reg(63 downto 32) <= Bus2IP_Data;
							when conv_std_logic_vector(TRIGGER_PATTERN_LOW, 5) =>
								trigger_pattern_reg(31 downto 0) <= Bus2IP_Data;
							when conv_std_logic_vector(TRIGGER_PATTERN_HIGH, 5) =>
								trigger_pattern_reg(63 downto 32) <= Bus2IP_Data;
							when conv_std_logic_vector(TRIGGER_DELAY, 5) =>
								trigger_delay_reg <= Bus2IP_Data;
							when conv_std_logic_vector(SAMPLE_COUNTER, 5) =>
								sample_counter_reg <= Bus2IP_Data;
							when conv_std_logic_vector(TEST_COUNTER, 5) =>
								test_counter_reg <= Bus2IP_Data;
							when others => null;
						end case;
					end if;
				end if;

				-- samplerate generator
				if samplerate_counter < samplerate_divider_reg then
					samplerate_counter <= samplerate_counter + 1;
				else
					samplerate_counter <= (others => '0');
				end if;

				-- write sample to memory, if running (TODO: maybe trigger outside of divided samplerate?)
				if control_reg(0) = '1' and samplerate_counter = 0 then
					if sample_counter_reg < conv_std_logic_vector(MEM_SIZE, 32) then
						sample_counter_reg <= sample_counter_reg + 1;
					end if;
					if write_index_reg < conv_std_logic_vector(MEM_SIZE, 32) then
						write_index_reg <= write_index_reg + 1;
					else
						write_index_reg <= (others => '0');
					end if;
					mem_data_in(0) <= input_state_reg;
					write_enable   <= '1';

					-- trigger logic
					if trigger_mask_reg > 0 then
						if (input_state_reg and trigger_mask_reg) = trigger_pattern_reg then
							control_reg(1) <= '1';
						end if;
					end if;
					for i in 0 to 63 loop
						if rising_trigger_reg(i) = '1' then
							if last_input_state(i) = '0' and input_state_reg(i) = '1' then
								control_reg(1) <= '1';
							end if;
						end if;
						if falling_trigger_reg(i) = '1' then
							if last_input_state(i) = '1' and input_state_reg(i) = '0' then
								control_reg(1) <= '1';
							end if;
						end if;
					end loop;

					-- sample trigger_delay_reg samples more after the trigger
					if control_reg(1) = '1' then
						if trigger_delay_reg > 0 then
							-- stop sampling
							control_reg(0) <= '0';
						else
							trigger_delay_reg <= trigger_delay_reg - 1;
						end if;
					end if;
				end if;

				-- increment counter, if enabled
				if control_reg(2) = '1' then
					test_counter_reg <= test_counter_reg + 1;
				end if;
			end if;
		end if;
	end process;

	-- drive IP to Bus signals
	IP2Bus_Data <= mem_ip2bus_data when mem_read_ack = '1' else (others => '0');
	IP2Bus_AddrAck <= mem_write_ack or (mem_read_enable and mem_read_ack);
	IP2Bus_WrAck   <= mem_write_ack;
	IP2Bus_RdAck   <= mem_read_ack;
	IP2Bus_Error   <= '0';

	-- debug output
	DBG <= Bus2IP_Clk & test_counter_reg(6 downto 0);

end IMP;
